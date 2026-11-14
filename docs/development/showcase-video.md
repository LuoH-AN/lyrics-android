# 交互介绍与 Actions 视频导出

官网入口为 `/showcase/`。这一页提供 18 秒产品介绍，可播放、暂停、拖动进度，并切换错落、居中和阶梯歌词。默认静音播放一次；用户点击后才能开启原创合成音乐。后台停止播放，系统减少动态效果时默认显示静态结尾。手机使用竖向构图，桌面与 MP4 使用横向构图。

这是产品概念演示，不是 Android 实机录屏。示例歌词、音乐和手机插画由本项目生成，没有引入第三方歌曲、图片或视频素材。

## 共用视觉与声音

- `website/showcase/scene.mjs`：纯时间驱动的 Canvas 场景；文字曲线、镜头和每个词的时间都从传入秒数计算。
- `website/showcase/audio.mjs`：固定种子的原创立体声合成配乐；浏览器与视频使用相同采样算法。
- `website/showcase/player.js`：网页交互、requestAnimationFrame、Web Audio 同步与生命周期。
- `tools/showcase-video/render.mjs`：Node 的 Skia Canvas 逐帧绘制相同场景，RGBA 通过有背压的管道直接送进 FFmpeg，不产生截图序列、不安装浏览器。

视频帧时间始终是 `frame / fps`，与 runner 渲染速度无关。导出为 H.264 / AAC MP4，BT.709、yuv420p、faststart。120 fps 是文件的帧率；播放流畅度仍取决于播放设备和软件。网页与视频共享字体、文字和轨迹，浏览器与 Skia 的栅格化可能存在细微差异。

## 使用 Actions

`Render introduction video` 在 main 的动画、字体或渲染工具改变时自动运行，也可手动触发：

- `fps`：120（默认）、60、30。
- `resolution`：1080p（默认）、720p。
- `layout`：auto（默认，依次演示三种排布）、staggered、centered、stairs。

作业使用 Node 22、固定版本 `@napi-rs/canvas` 和 FFmpeg；安装渲染依赖后直接编码，不运行 Node、Playwright 或 Android 测试。官网部署继续直接复制静态文件，不安装视频渲染依赖。视频单独作为 artifact 上传，保留 30 天；其中包含 MP4、编码信息 `render.json` 和 `SHA256SUMS`。不会将数十 MB 视频自动打进官网或 APK，也不会创建应用 Release。

字体是官方 Noto Sans SC 子集，沿用 `website/fonts/OFL.txt`。更新脚本同时生成 WOFF2 和供 Skia/浏览器共用的 TTF，字体生成只在维护时执行，需要 fontTools 与 Brotli，不在 CI 下载字体。

本地如需正式渲染（不是测试）：

```sh
cd tools/showcase-video
npm ci --ignore-scripts --no-audit --no-fund
VIDEO_FPS=120 VIDEO_RESOLUTION=1080p VIDEO_LAYOUT=auto npm run render
```

产物位于仓库的 `dist/showcase-video/`。需安装 FFmpeg，并使用存在 Canvas 预编译包的平台；日常推荐直接使用 GitHub Actions。
