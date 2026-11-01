# 桌面歌词官网

独立静态站，源码位于 `website/`。参考 [Agora 官网](https://agora.newoether.com/) 的留白、大标题与浅深色分区，以原创 HTML/CSS/JavaScript 实现；未复制其品牌、文案、字体文件或源代码。

## 视觉约束

- **禁止小字**：正文、导航、按钮、演示译文、FAQ、页脚在所有断点均不低于 18px。
- **移除装饰**：不用英文副标题、编号、角标、脚注、网格、轨道、装饰图标、阴影、旋转卡片或入场动画。
- 不内嵌缩小后带细字的应用截图，改为正常字号的仓库截图链接。
- 非必要内容直接删除，不把所有脚注放大塞回页面；必要的兼容、权限、来源与演示边界集中放进正常字号的 FAQ。

## 本地预览

需要 Node.js 22。页面无运行时依赖；仅浏览器测试需要 npm 安装开发依赖。

从仓库根目录运行：

```sh
node website/scripts/build.mjs
node website/scripts/check.mjs
node website/scripts/serve.mjs
```

打开 `http://127.0.0.1:4173`。预览服务只绑定本机，并且只提供构建后的站点文件，不暴露整个仓库。也可直接打开 `website/dist/index.html`。

## 页面内容

- 品牌首屏与真实 GitHub Releases 下载入口。
- 悬浮歌词网页演示：阅读／聊天背景、浅色／深色／暖橙、双语切换、下一句、复位。
- 拖动支持鼠标、触摸和键盘方向键；按住 Shift 可加大键盘移动步长。
- 多源查词说明、个性化／翻译／摘句介绍、应用界面链接、原生 FAQ、下载与项目来源。
- 响应式大字布局、屏幕阅读器标签、跳过导航、减少动态效果、无 JavaScript 降级。

演示文案为网页原创示例，非真实歌曲歌词；页面不读取音乐、麦克风或通知。应用截图仍在仓库 `docs/assets/` 维护，官网不内嵌带小字的缩略图。

网站不含统计脚本、外链字体、Cookie、存储、运行时 API 或第三方 CDN。用户点击外链后会访问对应服务。应用本身的联网行为另见使用指南，不能把网站的无 API 行为当作应用隐私承诺。

## 测试与产物

```sh
cd website
npm ci --ignore-scripts
npm run build
npm run check
npx playwright install chromium
npm test
```

Linux 浏览器所需系统库与中文字体需另行准备。`Website checks` 工作流在官网相关 PR 或手动触发时运行，在 GitHub 托管的临时 runner 安装测试依赖，不修改开发设备。

检查包括：320／390／768／1024／1440 宽度、页面错误、资源加载、无外部自动请求、无横向溢出、**所有可见文字计算字号不低于 18px**（含展开的 FAQ、阅读／聊天、全部示例歌词）、拖动／触摸／键盘、配色与双语切换、无 JS、FAQ 与减少动态效果。

工作流上传：

- `lyrics-website-static`：`website/dist/` 的完整可托管静态产物。
- `lyrics-website-preview`：页面截图、Playwright 报告和失败时的 trace。

此工作流**只检查与上传产物，不部署、不修改 Pages、不创建域名或 Release**。网页检查独立于 Android 构建，不引入 Android 运行依赖。

## 以后如何上线

确认页面并选定托管平台后，把 `website/dist/` 的内容作为站点根目录发布即可。资源使用相对路径，可在独立域名或 GitHub Pages 项目子路径下使用；可以部署到 GitHub Pages、Cloudflare Pages 等静态托管平台。

当前未填入虚构的生产域名，因此不生成 canonical、og:url 或 sitemap。确定域名后应补齐这些元数据，再按所选平台配置缓存和安全响应头。当前 PR 不包含自动部署，也不表示站点已经上线。

## 维护

- 文案与链接：`index.html`
- 配色、布局与响应式：`styles.css`
- 交互与原创示例文案：`app.js`
- 网站图标：仓库 `docs/assets/app-icon.svg`；应用截图仅链接到仓库查看
- 构建／预览／静态检查：`scripts/`
- 浏览器测试：`tests/` 与 `playwright.config.mjs`

保留仓库现有上游署名和许可边界。歌词平台名称只说明内容来源，不代表合作或兼容保证。
