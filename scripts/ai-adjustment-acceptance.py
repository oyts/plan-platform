"""Bounded selected-object acceptance. Isolated candidate only; no production data.

Default uses no provider. --real-max-calls 3 permits three new official requests,
with an exclusive persistent marker written BEFORE each submission, never retried.
"""
import argparse
import copy
import datetime as dt
import json
import os
from pathlib import Path
import secrets
import importlib.util

spec = importlib.util.spec_from_file_location('ai_acceptance', str(Path(__file__).with_name('ai-mysql-acceptance.py')))
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
Acceptance, api, require, private_write = base.Acceptance, base.api, base.require, base.private_write


class Adjustments(Acceptance):
    def fixture(self, mode):
        today = dt.date.today().isoformat()
        suffix = secrets.token_hex(4)
        if mode == 'TODO':
            return api('/todos', self.owner, {'title':'合成待办 '+suffix,'note':'仅验收内容','dueDate':today}, status=201)['id']
        if mode == 'PLAN':
            root = api('/plans', self.owner, {'name':'合成周期 '+suffix,'description':'仅验收内容','startDate':today,
                'endDate':(dt.date.today()+dt.timedelta(days=7)).isoformat(),'restDays':[]}, status=201)['id']
            for i in (1, 2):
                api('/plans/'+root+'/tasks', self.owner, {'date':today,'title':'合成任务 '+str(i),'description':'仅验收内容',
                    'category':'学习','plannedMinutes':30,'acceptance':['保留验收'],'required':True,'sortOrder':i}, status=201)
            first = self.sql("SELECT id FROM plan_tasks WHERE plan_id='%s' AND sort_order=1" % root)
            self.sql("UPDATE task_progress SET status='COMPLETED',completed_at=NOW(6),note='保留执行历史',version=version+1 WHERE task_id='%s'" % first)
            return root
        root = api('/mainlines', self.owner, {'name':'合成路线 '+suffix,'description':'仅验收内容','color':'#002FA7','executionMode':'SEQUENTIAL'}, status=201)['id']
        stage = api('/mainlines/'+root+'/stages', self.owner, {'name':'合成阶段','description':'保留阶段说明','sortOrder':0}, status=201)['id']
        for i in (1, 2):
            api('/mainlines/'+root+'/items', self.owner, {'title':'合成步骤 '+str(i),'description':'仅验收内容','plannedMinutes':30,
                'sortOrder':i,'acceptance':['保留验收'],'routing':{'stageId':stage,'dependencyMode':'INHERIT','prerequisiteIds':[]}}, status=201)
        return root

    def adjustment(self, mode, target):
        return api('/ai/adjustments', self.owner, {'mode':mode,'targetId':target,'consent':True})

    def validate_history(self, mode, target):
        if mode == 'PLAN':
            require(self.sql("SELECT p.note FROM task_progress p JOIN plan_tasks t ON t.id=p.task_id WHERE t.plan_id='%s' AND t.sort_order=1" % target) == '保留执行历史', 'Execution history changed')
        if mode == 'ROUTE':
            require(self.sql("SELECT COUNT(*) FROM mainline_items WHERE mainline_id='%s' AND dependency_mode='INHERIT'" % target) == '2', 'Unchanged inheritance lost')
            require(self.sql("SELECT description FROM mainline_stages WHERE mainline_id='%s'" % target) == '保留阶段说明', 'Stage description changed')

    def offline_adjustments(self):
        require(self.sql('SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1') == '17', 'V17 not applied')
        require(api('/ai/capabilities', self.owner)['adjustmentEnabled'], 'Adjustment capability missing')
        for mode in ('PLAN','ROUTE','TODO'):
            target = self.fixture(mode)
            api('/ai/adjustments', self.other, {'mode':mode,'targetId':target,'consent':True}, status=404)
            api('/ai/adjustments', self.owner, {'mode':mode,'targetId':target,'consent':False}, status=400)
            session = self.adjustment(mode, target)
            original = session['draft']
            require(original is not None, 'Selected object not captured')
            deletion = copy.deepcopy(original); deletion['items'].pop()
            api('/ai/sessions/'+session['id']+'/draft', self.owner, {'version':session['version'],'draftJson':json.dumps(deletion)}, method='PUT', status=422 if mode == 'TODO' else 400)
            draft = copy.deepcopy(original)
            if mode == 'PLAN':
                locked = session['adjustment']['protectedItems']
                require(len(locked) == 1, 'Completed task not protected')
                forbidden = copy.deepcopy(original); forbidden['items'][0]['title'] = '不应改写历史'
                api('/ai/sessions/'+session['id']+'/draft', self.owner, {'version':0,'draftJson':json.dumps(forbidden)}, method='PUT', status=400)
            draft['items'][-1]['title'] += '－手工调整'
            saved = self.save(session, draft)
            self.confirm(saved)
            self.validate_history(mode, target)
            stale = self.adjustment(mode, target)
            changed = copy.deepcopy(stale['draft']); changed['items'][-1]['title'] += '－过期建议'
            ready = self.save(stale, changed)
            table = {'PLAN':'plan_tasks','ROUTE':'mainline_items','TODO':'todos'}[mode]
            where = ("plan_id='%s' AND sort_order=2" if mode == 'PLAN' else "mainline_id='%s' AND sort_order=2" if mode == 'ROUTE' else "id='%s'") % target
            self.sql('UPDATE '+table+' SET version=version+1 WHERE '+where)
            api('/ai/sessions/'+ready['id']+'/apply', self.owner, {'version':ready['version'],'confirmed':True}, status=409)
            require(api('/ai/sessions/'+ready['id'], self.owner)['draft'] is not None, '409 lost draft')
        # Real MySQL CHECK failure after an existing item update must roll back both.
        target = self.fixture('TODO'); session = self.adjustment('TODO', target)
        draft = copy.deepcopy(session['draft']); original_title = draft['items'][0]['title']
        draft['items'][0]['title'] = '应回滚修改'
        added = copy.deepcopy(draft['items'][0]); added['localId']='new_item'; added['title']='故意失败 '+secrets.token_hex(4)
        draft['items'].append(added); ready = self.save(session, draft)
        owner = self.private['owner']['id']
        self.sql("ALTER TABLE todos ADD CONSTRAINT ai_adjust_rollback CHECK (owner_id <> '%s' OR title <> '%s')" % (owner, added['title']))
        try:
            api('/ai/sessions/'+ready['id']+'/apply', self.owner, {'version':ready['version'],'confirmed':True}, status=500)
            require(self.sql("SELECT title FROM todos WHERE id='%s'" % target) == original_title, 'Partial update committed')
            require(api('/ai/sessions/'+ready['id'], self.owner)['applied'] is None, 'Failed batch marked applied')
        finally:
            self.sql('ALTER TABLE todos DROP CHECK ai_adjust_rollback')
        require(api('/ai/quota', self.owner)['used'] == 0, 'Manual adjustment charged quota')
        private_write(self.directory/'adjustment-mysql-result.json', {'passed':True,'schema':17,'paidCalls':0,
            'checks':['three-mode adjustment','history/metadata protection','ownership/consent','no deletion','409 keeps draft','idempotent confirmation','whole transaction rollback']})
        print('PASS: V17/MySQL selected-object adjustment, history, ownership, 409, replay, rollback; paid calls=0.')

    def real_adjustments(self, bound):
        require(bound == 3, 'Exactly three bounded synthetic adjustment requests supported')
        require(api('/ai/capabilities', self.owner)['available'], 'Candidate AI not enabled')
        marker = self.directory/'adjustment-real-started.json'
        state = {'maxCalls':bound,'submitted':0,'results':[]}
        private_write(marker, state, exclusive=True)
        quota = api('/ai/quota', self.owner)
        require(quota['used'] == 0 and quota['pending'] == 0, 'Fresh synthetic quota required')
        owner = self.private['owner']['id']
        api('/admin/ai/users/'+owner, self.admin, {'dailyLimit':bound,'paused':False,'version':quota['version']}, method='PUT')
        try:
            for mode in ('PLAN','ROUTE','TODO'):
                target = self.fixture(mode); session = self.adjustment(mode,target)
                old = copy.deepcopy(session['draft'])
                message = '仅修改最后一个未开始事项的标题，在原标题末尾加上“－调整”。其他字段、日期、阶段、前置依赖、原事项标识全部保持不变，保留全部原事项，不增加不删除。直接返回完整修订草稿。'
                revised = self.turn(session,message,state,marker)
                require(revised['draft'] is not None, 'Official adjustment draft missing')
                require(revised['draft']['items'][-1]['title'] == old['items'][-1]['title']+'－调整', 'Requested adjustment not made')
                require(len(revised['draft']['items']) == len(old['items']), 'Original items changed count')
                self.confirm(revised); self.validate_history(mode,target)
            usage = api('/ai/quota',self.owner)
            require(usage['used'] == bound and usage['pending'] == 0, 'Paid count mismatch')
            state['passed'] = True
            print('PASS: three official selected-object adjustments, history retained, confirmed atomically; no retries.')
        finally:
            usage = api('/ai/quota',self.owner)
            api('/admin/ai/users/'+owner,self.admin,{'dailyLimit':0,'paused':True,'version':usage['version']},method='PUT')
            private_write(marker,state)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--real-max-calls',type=int,default=0);args=parser.parse_args()
    os.umask(0o077)
    acceptance=Adjustments(Path(__file__).resolve().parent.parent);acceptance.login()
    if args.real_max_calls: acceptance.real_adjustments(args.real_max_calls)
    else: acceptance.offline_adjustments()


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('Adjustment acceptance stopped: '+(str(error) if isinstance(error,RuntimeError) else type(error).__name__))
        raise SystemExit(1)
