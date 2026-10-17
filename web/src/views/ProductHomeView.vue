<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import QRCode from 'qrcode';
import { useAuthStore } from '@/stores/auth';
import { workspaceEntry } from '@/workspace';
import { useRouter } from 'vue-router';
import { parseProductDownloads, downloadSize, type ProductDownloads } from '@/productDownloads';
import AppSheet from '@/components/AppSheet.vue';
const auth = useAuthStore(); const router = useRouter();
const selected = ref(0); const open = ref(false); const qr = ref(''); const error = ref(''); const loading = ref(true);
const downloads = ref<ProductDownloads>({ windows: null, android: null });
function previewKey(event: KeyboardEvent, index: number) {
  let next: number;
  if (event.key === 'ArrowRight') next = (index + 1) % modes.length;
  else if (event.key === 'ArrowLeft') next = (index + modes.length - 1) % modes.length;
  else if (event.key === 'Home') next = 0;
  else if (event.key === 'End') next = modes.length - 1;
  else return;
  event.preventDefault(); selected.value = next; document.getElementById(`mode-tab-${next}`)?.focus();
}
const examples = [
  { prompt: '接下来两周，我想每周练三次吉他，每次半小时。', reason: '有明确周期，也有固定练习时间。', title: '两周吉他入门', items: ['第一周：认识吉他与基本姿势', '周一、周三、周五：单音练习', '第二周：练习简单和弦转换'] },
  { prompt: '我想从零学吉他，没有截止日期，有空就学一点。', reason: '按阶段慢慢推进，不必每天打卡。', title: '从零学习吉他', items: ['认识吉他：结构、调音与持琴', '开始演奏：单音与基础和弦', '练习一首喜欢的歌曲'] },
  { prompt: '今天想把练吉他的东西准备好，帮我列几件小事。', reason: '几件今天能处理的事，直接列成待办。', title: '准备开始练吉他', items: ['找出调音器，给吉他调音', '准备拨片和练习笔记', '选一首想学的歌曲'] },
];
const example = computed(() => examples[selected.value]!);
const aiSteps = [
  { title: '说说你想做什么', description: '用自己的话说明目标、可用时间和期限。不确定怎么开始，也可以直接聊。' },
  { title: '选一种合适的方式', description: 'AI 可以先推荐模式，由你确认；也可以从周期计划、目标路线或待办直接开始。' },
  { title: '一起把草稿改好', description: '先看结构和细节，再手工调整，或继续对话修改。不满意，不必从头填写。' },
  { title: '确认以后，再生成', description: '核对内容后，一次生成到对应模式。AI 的回复和草稿不会自行变成真实计划。' },
];
const modes = [
  { name: '周期计划', question: '这段时间，怎么安排？', description: '在一个明确的周期里安排日程、任务和休息日，记录计划与实际。', note: '适合备考、学期安排、规律训练。', steps: ['设定起止日期与休息日', '安排每日任务与验收标准', '记录用时，回顾计划与实际'] },
  { name: '目标路线', question: '这个目标，从哪里开始？', description: '把长线目标拆成阶段和步骤。有空就推进，不要求每天完成，也不需要预设结束日期。', note: '适合学吉他、技能学习、长期项目。', steps: ['拆出阶段与可执行步骤', '选择自由推进或前置依赖', '随时暂停，下次接着完成'] },
  { name: '今日待办', question: '今天，还有哪件事？', description: '随手记下一件事，安排日期，逐项完成。不用先创建一个长期计划。', note: '适合取快递、提交材料、临时事项。', steps: ['记下临时事项', '补充日期与备注', '完成后勾选，需要时再找回'] },
];
const mode = computed(() => modes[selected.value]!);
async function enter() { await router.push(auth.user ? await workspaceEntry() : '/login'); }
async function mobileDownload() {
  open.value = true; qr.value = '';
  if (downloads.value.android) {
    try { qr.value = await QRCode.toDataURL(downloads.value.android.url, { width: 256, margin: 2, errorCorrectionLevel: 'M', color: { dark: '#002FA7', light: '#FFFFFF' } }); }
    catch { error.value = '二维码生成失败，请使用直接下载链接。'; }
  }
}
onMounted(async () => {
  try {
    const response = await fetch('/downloads/manifest.json', { cache: 'no-store' });
    if (!response.ok) throw new Error('下载清单暂不可用。');
    downloads.value = parseProductDownloads(await response.json(), location.origin);
  } catch { error.value = '暂时无法获取下载清单，请稍后重试。'; }
  finally { loading.value = false; }
});
</script>

<template>
  <div class="product-home">
    <header class="product-header">
      <router-link to="/welcome" class="product-brand">计划舱<span>把想法，变成行动。</span></router-link>
      <nav aria-label="官网导航"><a href="#ways">使用方式</a><a href="#ai">AI 规划</a><a href="#clients">客户端下载</a><button class="btn primary" @click="enter">进入网页版</button></nav>
    </header>
    <section class="product-hero" aria-labelledby="product-title">
      <div class="product-hero-copy">
        <p class="product-intro">内置 AI 规划 · 三种独立工作模式</p>
        <h1 id="product-title">把今天安排好。<br/><span>让目标走得下去。</span></h1>
        <p class="product-description">有期限的安排，用周期计划。没期限的目标，用目标路线。临时想到的事，放进今日待办。</p>
        <p class="product-description">不知道怎么拆解？和 AI 聊聊，让想法先变成一份可以修改的草稿。</p>
        <div class="product-actions"><button class="btn primary" @click="enter">开始使用网页版</button><a class="btn" href="#ai">了解 AI 怎么帮忙</a></div>
        <p class="product-footnote">邀请制使用 · Windows / Android / 网页 · 开源项目</p>
      </div>
      <div class="product-preview" aria-label="AI 规划与三种模式示例">
        <div class="preview-heading"><strong>从一句话，到下一步。</strong><small>吉他学习示例</small></div>
        <div class="preview-tabs" role="tablist" aria-label="使用方式预览">
          <button v-for="(item,index) in modes" :id="`mode-tab-${index}`" :key="item.name" role="tab" :aria-selected="selected === index" :tabindex="selected === index ? 0 : -1" aria-controls="mode-preview" :class="{ active: selected === index }" @click="selected = index" @keydown="previewKey($event,index)">{{ item.name }}</button>
        </div>
        <div id="mode-preview" class="preview-content" role="tabpanel" :aria-labelledby="`mode-tab-${selected}`" tabindex="0">
          <div class="preview-request"><span>你的想法</span><p>{{ example.prompt }}</p></div>
          <div class="preview-recommendation"><span>建议使用 {{ mode.name }}</span><p>{{ example.reason }}</p></div>
          <div class="preview-draft"><div><h2>{{ example.title }}</h2><small>草稿示例</small></div><ol><li v-for="(item,index) in example.items" :key="item"><span>0{{ index + 1 }}</span>{{ item }}</li></ol><p>{{ mode.steps[2] }}</p></div>
        </div>
        <p class="preview-caption">这是功能演示，不会调用 AI 或创建计划。真实内容由你核对后确认。</p>
      </div>
    </section>
    <section id="ways" class="product-section" aria-labelledby="ways-title">
      <header><span>01</span><div><h2 id="ways-title">不是每件事，都需要同一种计划。</h2><p>按日程、按目标，或只管今天。三种方式，独立切换。</p></div></header>
      <div class="product-mode-grid"><article v-for="(item,index) in modes" :key="item.name"><span class="mode-number">0{{ index + 1 }}</span><h3>{{ item.name }}</h3><p>{{ item.description }}</p><small>{{ item.note }}</small><ol><li v-for="step in item.steps" :key="step">{{ step }}</li></ol></article></div>
      <p class="product-service-note">周期计划的每周休息日由你选择，也可二次编辑；目标路线支持阶段分组和前置依赖；今日待办不计入另外两种模式的完成率。</p>
    </section>
    <section id="ai" class="product-section" aria-labelledby="ai-title">
      <header><span>02</span><div><h2 id="ai-title">先聊清楚，再开始行动。</h2><p>内置 AI 不只生成一份清单，也陪你把规划改到适合自己。</p></div></header>
      <ol class="ai-workflow"><li v-for="(step,index) in aiSteps" :key="step.title"><span>0{{ index + 1 }}</span><h3>{{ step.title }}</h3><p>{{ step.description }}</p></li></ol>
      <div class="ai-adjustment"><div><p class="adjustment-label">已有计划，也能继续聊</p><h3>最近太忙？把节奏放慢一点。</h3><p>从具体计划、路线或待办进入“AI 调整”，只载入你选择的内容。先看前后差异，再决定是否应用；已完成事项和执行历史受到保护，不会把原计划删掉重建。</p></div><ol><li>选择要调整的对象</li><li>说明新的时间或要求</li><li>核对差异，确认应用</li></ol></div>
      <p class="product-service-note">内置 AI 使用 DeepSeek 官方 API，发送前需要你的同意。AI 需要联网，每日可用次数以账号页面为准；暂时不可用时，仍可手动规划，也可导入符合格式的外部 AI 草稿。</p>
    </section>
    <section class="product-section" aria-labelledby="devices-title">
      <header><span>03</span><div><h2 id="devices-title">电脑上规划，手机上接着做。</h2><p>同一个云端账号，在网页、Windows 和 Android 继续自己的行动。</p></div></header>
      <div class="product-feature-grid"><article><h3>同一个账号，同一份进度</h3><p>三端连接同一服务器后同步数据。每个用户的计划独立保存；需要时可导出 CSV 或 Markdown。</p></article><article><h3>没网时，先把想法留住</h3><p>已缓存内容可离线浏览，支持部分新增与编辑。操作先留在本机，联网后在同步中心继续；未缓存的内容和 AI 对话仍需联网。</p></article><article><h3>两端改了，也不会悄悄覆盖</h3><p>同一条记录发生冲突时，保留本地操作，交给你比较处理。更新安装也需要确认，自动下载默认关闭。</p></article></div>
    </section>
    <section class="product-section product-learning" aria-labelledby="learning-title">
      <header><span>04</span><div><h2 id="learning-title">第一次用，先亲手做一个小案例。</h2><p>不必对着空白页面摸索。跟着步骤试一遍，忘记了随时回来重练。</p></div></header>
      <div class="learning-layout"><div class="learning-copy"><h3>在独立练习区里，放心试。</h3><p>教学不会改动真实计划。AI 教学使用模拟过程，不调用模型、不消耗对话次数；练习进度保存在当前账号与设备中。</p><button class="btn" @click="router.push('/learn')">{{ auth.user ? '打开使用教学' : '登录后开始教学' }}</button></div><ol class="learning-courses"><li><span>01</span>周期计划<small>安排周期与休息日</small></li><li><span>02</span>目标路线<small>拆出阶段与步骤</small></li><li><span>03</span>今日待办<small>记录并完成一件事</small></li><li><span>04</span>离线与同步<small>理解本地保存与冲突</small></li><li><span>05</span>AI 规划<small>从说明目标到确认生成</small></li></ol></div>
    </section>
    <section id="clients" class="product-section" aria-labelledby="clients-title">
      <header><span>05</span><div><h2 id="clients-title">选一个入口，开始使用。</h2><p>网页版直接登录；Windows 可下载安装；Android 目前仅供受邀测试。</p></div></header>
      <p v-if="error" class="error-text" role="status">{{ error }}</p>
      <div class="product-client-grid"><article><span class="client-label">无需安装</span><h3>网页版</h3><p>电脑和手机浏览器均可访问。已有账号可直接登录，新成员请先联系管理员获取邀请。</p><button class="btn primary" @click="enter">进入网页版</button></article><article><span class="client-label">桌面客户端</span><h3>Windows 电脑</h3><p>独立客户端，支持缓存与离线编辑。新版本在应用内提示，由你决定何时下载和安装。</p><a v-if="downloads.windows" class="btn primary" :href="downloads.windows.url" download>下载 Windows 安装包</a><button v-else class="btn" disabled>{{ loading ? '检查发布状态…' : '正式安装包尚未发布' }}</button><small v-if="downloads.windows">{{ downloads.windows.version }} · {{ downloadSize(downloads.windows.bytes) }}</small></article><article><span class="client-label">{{ downloads.android ? '手机客户端' : '受邀测试' }}</span><h3>Android 手机</h3><p v-if="downloads.android">扫描二维码或直接下载安装包，登录同一个账号继续使用。</p><p v-else>暂不提供公开 APK 和二维码。首次安装请联系邀请人，已安装的受邀成员可登录后在应用内检查更新。</p><button class="btn" :disabled="loading" @click="mobileDownload">{{ downloads.android ? '扫码下载 Android' : '查看 Android 发布状态' }}</button><small v-if="downloads.android">{{ downloads.android.version }} · {{ downloadSize(downloads.android.bytes) }}</small><small v-else>目前可先使用手机浏览器访问网页版。</small></article></div>
    </section>
    <section class="product-section product-faq" aria-labelledby="faq-title"><header><span>06</span><div><h2 id="faq-title">开始前，你可能想知道。</h2></div></header><div class="faq-list"><details><summary>现在可以直接注册吗？</summary><p>目前采用管理员邀请制，没有公众注册入口。已有账号可直接登录；忘记密码时，可从登录页通过账号邮箱找回。</p></details><details><summary>我一定要使用 AI 吗？</summary><p>不需要。三种模式都支持手动创建和修改。AI 是可选的规划助手，你可以先看草稿、继续讨论，再决定是否生成或应用。</p></details><details><summary>我的所有计划都会发给 AI 吗？</summary><p>不会自动扫描全部计划。新建时发送本次对话与草稿；调整已有内容时，先由你选择对象并同意载入必要内容。请勿输入密码、身份证件等敏感信息。</p></details><details><summary>离线时，哪些事情还能做？</summary><p>先联网加载需要的内容后，可以离线浏览已缓存页面，并对支持的事项进行新增或编辑。联网后在同步中心处理本地操作与冲突；AI、计时和完成类操作仍需要联网。</p></details></div></section>
    <footer class="product-footer"><div><strong>计划舱</strong><span>按自己的节奏，认真做下一件事。</span></div><a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">鄂ICP备2026050813号-1</a><div class="footer-links"><a href="https://github.com/oyts/plan-platform" target="_blank" rel="noopener noreferrer">GitHub 源码</a><router-link to="/login">账号登录</router-link></div></footer>
    <AppSheet :open="open" title="Android 客户端下载" title-id="android-download-title" @close="open = false"><template v-if="downloads.android"><p>扫描二维码，在手机浏览器中下载 APK。</p><img v-if="qr" class="download-qr" :src="qr" alt="Android 安装包下载二维码" width="256" height="256"/><p><a class="btn primary" :href="downloads.android.url" download>直接下载 APK</a></p><p>{{ downloads.android.version }} · {{ downloadSize(downloads.android.bytes) }}</p><details><summary>查看 SHA-256 校验值</summary><code class="download-hash">{{ downloads.android.sha256 }}</code></details><p class="helper-text">安装时 Android 可能要求允许浏览器安装应用。只从本站获取安装包；二维码不包含账号或密码。</p></template><template v-else><p>Android 客户端目前仅供受邀测试，暂不提供公开下载及二维码。</p><p>已安装受邀测试版的成员，可登录后在“客户端更新”中检查新版本。第一次安装请联系邀请人；也可先使用网页版。</p></template></AppSheet>
  </div>
</template>
<style scoped>
.product-home{background:#fff;color:#111827;font-family:"Helvetica Neue",Arial,"Microsoft YaHei",sans-serif}.product-header{max-width:1400px;margin:auto;padding:24px 40px;border-bottom:1px solid #d7dce4;display:flex;justify-content:space-between;align-items:center;gap:24px}.product-brand{font-size:24px;letter-spacing:-.05em;font-weight:900;color:#002fa7;text-decoration:none}.product-header nav{display:flex;align-items:center;gap:28px}.product-header nav>a{font-size:14px;color:#111827;text-decoration:none}.product-hero{max-width:1400px;margin:auto;display:grid;grid-template-columns:1.15fr 1fr;align-items:center;gap:56px;padding:88px 40px}.product-intro{font-size:14px;color:#002fa7;font-weight:700;margin-bottom:30px}.product-hero h1{font-size:clamp(38px,4.5vw,66px);letter-spacing:-.065em;line-height:1.2;margin-bottom:30px}.product-hero h1 span{color:#002fa7}.product-description{font-size:17px;line-height:1.8;color:#667085}.product-actions{display:flex;gap:12px;margin:30px 0 20px}.product-actions .btn{padding:14px 24px;text-decoration:none;display:inline-flex;align-items:center}.product-footnote{color:#667085;font-size:13px}.product-preview{border:1px solid #9ba3b0}.preview-heading{display:flex;justify-content:space-between;gap:16px;align-items:center;padding:20px;border-bottom:1px solid #d7dce4}.preview-heading small{color:#667085}.preview-tabs{display:flex;border-bottom:1px solid #9ba3b0}.preview-tabs button{flex:1;min-height:52px;background:#fff;border:0;border-right:1px solid #d7dce4;cursor:pointer;font-size:14px}.preview-tabs button:last-child{border-right:0}.preview-tabs .active{background:#002fa7;color:#fff}.preview-content{padding:30px;min-height:325px}.preview-number{font-size:52px;color:#002fa7;font-weight:800;line-height:1}.preview-content h2{font-size:23px;margin:20px 0}.preview-content ol{padding:0;list-style:none;counter-reset:steps}.preview-content li{padding:12px 0;border-top:1px solid #d7dce4;font-size:15px;counter-increment:steps}.preview-content li:before{content:counter(steps,decimal-leading-zero);margin-right:18px;color:#002fa7}.preview-content p{font-size:13px;color:#667085;margin:20px 0 0}.product-section{max-width:1320px;margin:0 auto;border-top:1px solid #9ba3b0;padding:40px 0 60px;scroll-margin-top:20px}.product-section>header{display:flex;gap:28px;align-items:start;margin-bottom:32px}.product-section>header>span{font-size:42px;color:#002fa7;font-weight:800;line-height:1}.product-section h2{font-size:30px;letter-spacing:-.04em}.product-section header p{color:#667085;margin:0}.product-mode-grid,.product-feature-grid,.product-client-grid{display:grid;grid-template-columns:repeat(3,1fr);border-top:1px solid #d7dce4;border-left:1px solid #d7dce4}.product-mode-grid article,.product-feature-grid article,.product-client-grid article{padding:32px;border-right:1px solid #d7dce4;border-bottom:1px solid #d7dce4}.mode-number{color:#002fa7;font-size:15px}.product-mode-grid h3{font-size:25px;margin:22px 0 16px}.product-mode-grid p,.product-feature-grid p,.product-client-grid p{font-size:15px;color:#667085;line-height:1.8}.product-mode-grid small{font-size:13px}.product-client-grid article{display:flex;flex-direction:column;align-items:start}.product-client-grid p{flex:1}.product-client-grid a{display:inline-flex;text-decoration:none;align-items:center}.product-client-grid small{display:block;color:#667085;font-size:12px;line-height:1.6;margin-top:14px}.product-footer{max-width:1320px;margin:auto;padding:30px 0;border-top:1px solid #9ba3b0;display:flex;justify-content:space-between;align-items:center;gap:20px;font-size:13px}.product-footer>div{display:grid;gap:8px}.product-footer strong{font-size:18px;color:#002fa7}.product-footer span{color:#667085}.product-footer a{text-decoration:none}.download-qr{display:block;max-width:100%;height:auto;margin:20px auto}.download-hash{display:block;overflow-wrap:anywhere;padding:12px 0}
@media(max-width:1400px){.product-section,.product-footer{margin-left:40px;margin-right:40px}}
@media(max-width:900px){.product-header{padding:20px 24px}.product-header nav{gap:14px}.product-hero{grid-template-columns:1fr;gap:32px;padding:48px 24px}.product-section,.product-footer{margin-left:24px;margin-right:24px}.product-mode-grid,.product-feature-grid,.product-client-grid{grid-template-columns:1fr}.product-section h2{font-size:26px}.product-mode-grid article,.product-feature-grid article,.product-client-grid article{padding:24px}.product-footer{flex-wrap:wrap}.product-footer>div{width:100%}}
@media(max-width:560px){.product-header{flex-wrap:wrap;gap:20px}.product-header nav{width:100%;justify-content:space-between}.product-header nav .btn{font-size:13px}.product-hero h1{font-size:36px;line-height:1.25}.product-description{font-size:15px}.product-actions .btn{padding:12px 16px;font-size:14px}.product-hero{padding-top:36px}.preview-content{padding:24px}.product-section>header{gap:18px}.product-section h2{font-size:23px}.product-section>header>span{font-size:34px}.product-footer{padding-bottom:32px}}

.product-home .btn{min-height:44px}.product-home a:focus-visible,.product-home button:focus-visible,.product-home summary:focus-visible{outline:2px solid #002fa7;outline-offset:4px}.product-brand{display:flex;align-items:center;gap:18px}.product-brand span{font-size:12px;font-weight:400;letter-spacing:0;color:#667085}.product-hero{grid-template-columns:1fr 1fr;gap:60px;padding-top:72px;padding-bottom:80px}.product-description{max-width:490px;font-size:16px;line-height:1.9;margin-bottom:16px}.product-actions{flex-wrap:wrap}.product-footnote{font-size:12px;line-height:1.8}.product-preview{min-width:0}.preview-heading strong{font-size:16px}.preview-heading small{font-size:12px}.preview-tabs button{min-width:0}.preview-content{min-height:420px;padding:24px}.preview-request{display:grid;grid-template-columns:64px 1fr;gap:16px;padding-bottom:20px;border-bottom:1px solid #d7dce4}.preview-request>span{font-size:12px;color:#667085;line-height:1.8}.preview-content .preview-request p{font-size:15px;line-height:1.8;color:#111827;margin:0}.preview-recommendation{padding:18px 0}.preview-recommendation>span{font-size:13px;font-weight:800;color:#002fa7}.preview-content .preview-recommendation p{font-size:12px;margin:6px 0 0}.preview-draft{border:1px solid #d7dce4}.preview-draft>div{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:16px;background:#f7f7f8}.preview-content .preview-draft h2{font-size:17px;margin:0}.preview-draft small{font-size:11px;color:#667085;white-space:nowrap}.preview-content .preview-draft ol{margin:0}.preview-content li{display:flex;gap:14px;padding:12px 16px;font-size:13px;line-height:1.7}.preview-content li:before{display:none}.preview-content li span{color:#002fa7;font-variant-numeric:tabular-nums}.preview-content .preview-draft>p{margin:0;padding:12px 16px;font-size:11px;line-height:1.7;border-top:1px solid #d7dce4}.preview-caption{padding:14px 20px;margin:0;background:#f7f7f8;border-top:1px solid #d7dce4;font-size:11px;line-height:1.8;color:#667085}.product-section h2{line-height:1.4}.product-section header p{font-size:15px;line-height:1.8}.product-mode-grid,.product-feature-grid,.product-client-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.product-mode-grid article,.product-feature-grid article,.product-client-grid article{min-width:0;padding:28px}.product-mode-grid p,.product-feature-grid p,.product-client-grid p{font-size:14px;line-height:1.9}.product-mode-grid small{line-height:1.8;font-size:12px}.product-mode-grid ol{padding:16px 0 0;margin:20px 0 0;border-top:1px solid #d7dce4;list-style:none}.product-mode-grid li{font-size:12px;color:#667085;padding:6px 0}.product-service-note{font-size:12px;line-height:1.9;color:#667085;margin:18px 0 0;max-width:1050px}.ai-workflow{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));list-style:none;padding:0;margin:0;border:1px solid #d7dce4;border-right:0}.ai-workflow>li{padding:24px;border-right:1px solid #d7dce4}.ai-workflow>li>span{color:#002fa7;font-size:36px;font-variant-numeric:tabular-nums}.ai-workflow h3{font-size:18px;margin:26px 0 14px;line-height:1.5}.ai-workflow p{font-size:13px;line-height:1.9;color:#667085;margin:0}.ai-adjustment{display:grid;grid-template-columns:1.4fr 1fr;gap:60px;padding:32px;margin-top:20px;background:#002fa7;color:#fff}.adjustment-label{font-size:12px;margin-bottom:18px}.ai-adjustment h3{font-size:25px;line-height:1.5;margin-bottom:16px}.ai-adjustment>div>p:last-child{font-size:14px;line-height:1.9;margin:0}.ai-adjustment ol{padding:0;margin:0;list-style:none;counter-reset:adjustment;align-self:center}.ai-adjustment li{padding:16px 0;border-top:1px solid #ffffff55;font-size:14px;counter-increment:adjustment}.ai-adjustment li:before{content:counter(adjustment,decimal-leading-zero);margin-right:22px}.product-feature-grid h3{font-size:19px;line-height:1.5}.product-feature-grid p{margin:0}.learning-layout{display:grid;grid-template-columns:1fr 1fr;border:1px solid #d7dce4}.learning-copy{padding:32px;align-self:center;max-width:520px}.learning-copy h3{font-size:24px;line-height:1.5}.learning-copy p{font-size:14px;line-height:1.9;color:#667085;margin-bottom:24px}.learning-courses{padding:0;margin:0;list-style:none;border-left:1px solid #d7dce4}.learning-courses li{display:grid;grid-template-columns:32px 100px 1fr;align-items:center;gap:20px;padding:20px 24px;border-bottom:1px solid #d7dce4;font-size:14px}.learning-courses li:last-child{border:0}.learning-courses span{color:#002fa7}.learning-courses small{color:#667085;font-size:12px}.product-client-grid h3{font-size:24px;margin:24px 0 16px}.client-label{color:#002fa7;font-size:12px}.product-faq{display:grid;grid-template-columns:.9fr 1.1fr;gap:48px}.product-faq>header{margin:0}.faq-list{border-top:1px solid #d7dce4}.faq-list details{padding:20px 0;border-bottom:1px solid #d7dce4}.faq-list summary{font-size:15px;font-weight:700;cursor:pointer;line-height:1.8}.faq-list p{font-size:14px;line-height:1.9;color:#667085;margin:16px 0 0}.product-footer>.footer-links{display:flex;gap:24px}
@media(max-width:1100px){.product-brand span{display:none}.product-hero{gap:32px}.learning-courses li{gap:14px;grid-template-columns:24px 84px 1fr}.product-faq{grid-template-columns:1fr}}
@media(max-width:900px){.product-hero{grid-template-columns:1fr;padding:48px 24px}.product-description{max-width:650px}.product-mode-grid,.product-feature-grid,.product-client-grid{grid-template-columns:1fr}.product-mode-grid ol{display:none}.product-mode-grid h3{margin:14px 0 12px}.product-mode-grid p{margin-bottom:12px}.ai-workflow{grid-template-columns:repeat(2,minmax(0,1fr))}.ai-workflow>li:nth-child(-n+2){border-bottom:1px solid #d7dce4}.ai-workflow h3{margin-top:20px}.ai-adjustment{gap:28px;padding:24px}.ai-adjustment h3{font-size:23px}.learning-layout{grid-template-columns:1fr}.learning-copy{max-width:none;padding:24px}.learning-courses{border-left:0;border-top:1px solid #d7dce4}.learning-courses li{grid-template-columns:24px 100px 1fr}}
@media(max-width:560px){.product-header{padding:20px;gap:18px}.product-header nav{gap:12px;flex-wrap:wrap}.product-header nav>a{font-size:12px}.product-header nav .btn{font-size:12px;margin-left:auto;padding:8px 12px}.product-hero{padding:36px 20px 44px}.product-intro{font-size:12px;margin-bottom:22px}.product-hero h1{margin-bottom:24px}.product-description{font-size:14px;line-height:1.85}.product-actions{gap:10px}.product-actions .btn{padding:12px 14px;font-size:13px}.product-footnote{font-size:11px}.preview-heading{padding:16px;gap:10px}.preview-heading strong{font-size:15px}.preview-heading small{font-size:10px}.preview-tabs button{font-size:13px}.preview-content{padding:18px;min-height:420px}.preview-request{grid-template-columns:1fr;gap:8px;padding-bottom:16px}.preview-content .preview-request p{font-size:14px}.preview-draft>div{padding:14px 12px}.preview-content .preview-draft h2{font-size:15px}.preview-content li{font-size:12px;padding:12px;gap:10px}.preview-content .preview-draft>p{padding:12px}.preview-caption{padding:12px 16px}.product-section,.product-footer{margin-left:20px;margin-right:20px}.product-section{padding:30px 0 40px}.product-section>header{gap:16px;margin-bottom:24px}.product-section header p{font-size:13px}.product-mode-grid article,.product-feature-grid article,.product-client-grid article{padding:22px}.ai-workflow>li{padding:18px 16px}.ai-workflow>li>span{font-size:29px}.ai-workflow h3{font-size:16px;margin:18px 0 12px}.ai-workflow p{font-size:12px}.ai-adjustment{grid-template-columns:1fr;gap:20px;padding:22px}.ai-adjustment h3{font-size:22px}.ai-adjustment>div>p:last-child{font-size:13px}.ai-adjustment li{font-size:13px;padding:13px 0}.product-service-note{font-size:11px}.product-feature-grid h3{font-size:18px}.learning-copy h3{font-size:21px}.learning-copy p{font-size:13px}.learning-courses li{padding:18px;grid-template-columns:20px 88px 1fr;gap:10px;font-size:13px}.learning-courses small{font-size:11px}.product-faq{gap:0}.faq-list summary{font-size:14px}.faq-list p{font-size:13px}.product-footer{gap:20px;font-size:11px}.product-footer>.footer-links{gap:20px;width:auto}}

</style>
