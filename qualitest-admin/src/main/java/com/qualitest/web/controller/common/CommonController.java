package com.qualitest.web.controller.common;

import com.qualitest.common.config.QualitestConfig;
import com.qualitest.common.core.domain.AjaxResult;
import com.qualitest.common.utils.StringUtils;
import com.qualitest.common.utils.file.FileUtils;
import com.qualitest.file.storage.FileStorageService;
import com.qualitest.file.storage.UploadResult;
import com.qualitest.framework.config.ServerConfig;
import com.qualitest.project.support.cursorskill.McpCursorSkillService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用请求处理：上传下载、以及全局 Cursor MCP Skill 正文拉取等。
 *
 * @author qualitest
 */
@RestController
@RequestMapping("/common")
public class CommonController {
    private static final Logger log = LoggerFactory.getLogger(CommonController.class);
    private static final String FILE_DELIMITER = ",";
    @Autowired
    private ServerConfig serverConfig;
    /** 读取 MCP 造流 Skill 全文（全局，不绑项目） */
    @Autowired
    private McpCursorSkillService mcpCursorSkillService;
    /** 统一文件存储（按配置写入本地盘 / RustFS / 七牛） */
    @Autowired
    private FileStorageService fileStorageService;

    /**
     * 通用下载请求
     *
     * @param fileName 文件名称
     * @param delete   是否删除
     */
    @GetMapping("/download")
    public void fileDownload(String fileName, Boolean delete, HttpServletResponse response, HttpServletRequest request) {
        try {
            if (!FileUtils.checkAllowDownload(fileName)) {
                throw new Exception(StringUtils.format("文件名称({})非法，不允许下载。 ", fileName));
            }
            String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
            String filePath = QualitestConfig.getDownloadPath() + fileName;

            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, realFileName);
            FileUtils.writeBytes(filePath, response.getOutputStream());
            if (delete) {
                FileUtils.deleteFile(filePath);
            }
        } catch (Exception e) {
            log.error("下载文件失败", e);
        }
    }

    /**
     * 通用上传（单个）。浏览器把文件交给本接口，服务端再写入当前配置的存储后端。
     * <p>
     * 返回字段说明：fileName 为建议入库路径（本地相对路径或云完整 URL）；url 为可直接访问地址；
     * objectKey 为对象键；storage 为实际后端标识。
     */
    @PostMapping("/upload")
    public AjaxResult uploadFile(MultipartFile file) throws Exception {
        try {
            UploadResult result = fileStorageService.upload(file);
            String fileName = result.getFilePath();
            String url = fileStorageService.resolveAccessUrl(fileName, serverConfig.getUrl());
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("fileName", fileName);
            ajax.put("newFileName", FileUtils.getName(fileName));
            ajax.put("originalFilename", file.getOriginalFilename());
            ajax.put("objectKey", result.getObjectKey());
            ajax.put("storage", result.getStorage());
            return ajax;
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 通用上传（多个）。逐个走同一套存储服务，结果字段用英文逗号拼接。
     */
    @PostMapping("/uploads")
    public AjaxResult uploadFiles(List<MultipartFile> files) throws Exception {
        try {
            List<String> urls = new ArrayList<String>();
            List<String> fileNames = new ArrayList<String>();
            List<String> newFileNames = new ArrayList<String>();
            List<String> originalFilenames = new ArrayList<String>();
            for (MultipartFile file : files) {
                UploadResult result = fileStorageService.upload(file);
                String fileName = result.getFilePath();
                String url = fileStorageService.resolveAccessUrl(fileName, serverConfig.getUrl());
                urls.add(url);
                fileNames.add(fileName);
                newFileNames.add(FileUtils.getName(fileName));
                originalFilenames.add(file.getOriginalFilename());
            }
            AjaxResult ajax = AjaxResult.success();
            ajax.put("urls", StringUtils.join(urls, FILE_DELIMITER));
            ajax.put("fileNames", StringUtils.join(fileNames, FILE_DELIMITER));
            ajax.put("newFileNames", StringUtils.join(newFileNames, FILE_DELIMITER));
            ajax.put("originalFilenames", StringUtils.join(originalFilenames, FILE_DELIMITER));
            return ajax;
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 返回 MCP 造流弹框载荷：人话用法、示例提问、各编辑器规程 Tab。
     * 需登录。可选传测试项目 id：有则按该项目写流/导入开关裁剪规程正文；无则按只读裁剪。
     *
     * @param testProjectId 测试项目 id，可空
     * @return 弹框载荷
     */
    @GetMapping("/mcpAgentGuides")
    public AjaxResult mcpAgentGuides(Long testProjectId) {
        return AjaxResult.success("操作成功", mcpCursorSkillService.loadPayload(testProjectId));
    }

    /**
     * 本地资源通用下载
     */
    @GetMapping("/download/resource")
    public void resourceDownload(String resource, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        try {
            if (!FileUtils.checkAllowDownload(resource)) {
                throw new Exception(StringUtils.format("资源文件({})非法，不允许下载。 ", resource));
            }
            // 本地资源路径
            String localPath = QualitestConfig.getProfile();
            // 数据库资源地址
            String downloadPath = localPath + FileUtils.stripPrefix(resource);
            // 下载名称
            String downloadName = StringUtils.substringAfterLast(downloadPath, "/");
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, downloadName);
            FileUtils.writeBytes(downloadPath, response.getOutputStream());
        } catch (Exception e) {
            log.error("下载文件失败", e);
        }
    }
}
