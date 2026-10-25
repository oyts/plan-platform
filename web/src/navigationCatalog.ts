import { planNavigation, routeNavigation, todoNavigation, type WorkspaceMode } from './workspaceModel';
export interface NavigationItem {path:string;label:string;description:string}
export interface NavigationGroup {title:string;items:NavigationItem[]}
export function primaryNavigation(mode:WorkspaceMode){return mode==='plan'?planNavigation:mode==='route'?routeNavigation:todoNavigation;}
export function navigationGroups(mode:WorkspaceMode,admin:boolean,native:boolean):NavigationGroup[]{
  return [
    {title:'当前工作区',items:mode==='plan'?[
      {path:'/stats',label:'统计',description:'计划与实际用时'},
      {path:'/reviews',label:'复盘',description:'回顾每天的执行'},
      {path:'/records',label:'记录',description:'计时流水与回收站'},
    ]:mode==='route'?[{path:'/route-records',label:'记录',description:'已删除的路线与步骤'}]:[]},
    {title:'规划与回顾',items:[
      {path:'/ai',label:'AI 规划',description:'对话、草稿与确认生成'},
      {path:'/overview',label:'今日与提醒',description:'今日、逾期与即将到期'},
      {path:'/reminders',label:'系统提醒',description:'本设备授权、提前与收尾通知'},
      {path:'/weekly-review',label:'周复盘',description:'本周推进与下周重点'},
      {path:'/reflection',label:'日周复盘',description:'三模式事实、自述与AI分析'},
      {path:'/organize',label:'编排与历史',description:'整套复制、未来调整与分页查询'},
      {path:'/templates',label:'个人模板库',description:'收藏与复用整套计划或路线'},
    ]},
    {title:'数据与连接',items:[
      {path:'/exports',label:'数据中心',description:'三模式导出与私有备份恢复'},
      {path:'/sync',label:'同步',description:'待同步操作与冲突'},
      {path:'/offline',label:'离线范围',description:'缓存清单与有限日程预加载'},
      ...(native?[{path:'/desktop',label:'客户端',description:'服务器连接与更新'}]:[]),
    ]},
    {title:'帮助与账号',items:[
      {path:'/help',label:'帮助与版本',description:'使用说明、能力边界与本地演示'},
      {path:'/learn',label:'使用教学',description:'分步练习与随时回顾'},
      {path:'/feedback',label:'反馈与诊断',description:'本机草稿与处理回复'},
      {path:'/settings',label:'设置',description:'账号与安全'},
    ]},
    ...(admin?[{title:'管理员',items:[
      {path:'/admin',label:'管理中心',description:'成员、AI资源、运行与反馈处理'},
    ]}]:[]),
  ].filter(group=>group.items.length);
}
