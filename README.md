# DownloadManager
Android基于Okhttp的一个上传、下载框架

这个主要库提供上传、下载功能。
- 上传：主要使用kotlin+协程+retrofit
  - 不需要进度的上传
  - 需要进度的上传
  - 批量上传

- 下载
  - kotlin+协程：主要是单个下载，比如app更新
  - 本库中的 **DownloadManager** 主要用于列表下载

- TODO
  - 增加多线程下载
  - 增加断点续传、断点下载
