package com.progbits.mvn.coffer;

import com.progbits.jetty.embedded.router.JettyEmbeddedRequest;
import com.progbits.jetty.embedded.router.JettyEmbeddedResponse;
import com.progbits.jetty.embedded.router.ServletRouter;
import com.progbits.jetty.embedded.servlet.ServletController;
import com.progbits.jetty.embedded.util.HttpReqHelper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main Servlet for the Project
 *
 * @author scarr
 */
public class MvnCofferController implements ServletController {

    private Logger _log = LogManager.getLogger(MvnCofferController.class);

    private static final String ALIAS = "/coffer/repository";
    private static final String ALIAS_PATH = "/coffer/repository${fileLoc}";

    private Map<String, LoginUser> _users = new HashMap<>();

    private String _storePath = System.getProperty("karaf.base") + "/data/coffer";
    private Path _storage;

    public String getStorePath() {
        return _storePath;
    }

    public void setStorePath(String _storePath) {
        this._storePath = _storePath;
    }

    public MvnCofferController() {
        
    }

    private void update() {
        _storePath = System.getenv("COFFER_REPO_DIR");

        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            if (entry.getKey().startsWith("COFFER_USER_")) {
                String user = entry.getKey().replace("COFFER_USER_", "");

                String[] sPassRoles = entry.getValue().split(";");

                _users.put(user, new LoginUser(user, sPassRoles[0], sPassRoles[1]));
            }
        }
    }

    @Override
    public void init() {
        update();
        
        // Create the directory if it is not there
        _storage = Paths.get(_storePath);

        if (Files.notExists(_storage)) {
            try {
                Files.createDirectories(_storage);
            } catch (Exception ex) {
                _log.error("init", ex);
            }
        }
    }

    @Override
    public void routes(ServletRouter route) {
        route.get("/coffer/healthcheck", this::healthCheck);
        route.custom("HEAD", ALIAS_PATH, this::processRepoGetHead, null, null);
        route.get(ALIAS_PATH, this::processRepoGetHead, null);
        route.post(ALIAS_PATH, this::processRepoSave, null, null);
        route.put(ALIAS_PATH, this::processRepoSave, null, null);
    }

    private void healthCheck(JettyEmbeddedRequest req, JettyEmbeddedResponse resp) throws Exception {
        HttpReqHelper.sendString(resp.getResponse(), 200, "text/plain", "Ok");
        resp.status(200);
    }

    private void processRepoSave(JettyEmbeddedRequest req, JettyEmbeddedResponse resp) throws Exception {
        LoginUser lu = authorizeUser(req, resp);

        if (lu == null) {
            return;
        }

        String strLoc = req.getRequestInfo().getString("fileLoc");

        _log.info("PUT Path: {}", strLoc);

        Path fSet = Paths.get(_storePath, strLoc);

        String[] sSplitPaths = strLoc.split("/");

        if (lu.hasRole(sSplitPaths[1] + "_WRITE")) {
            if (!strLoc.contains(".xml") && Files.exists(fSet)) {
                HttpReqHelper.sendString(resp.getResponse(), 409, "text/plain", "Overwriting Release Not Allowed");
            } else {
                if (strLoc.contains("maven-metadata.xml") && Files.exists(fSet)) {
                    Files.delete(fSet);
                }

                Files.createDirectories(fSet.getParent());

                Files.copy(req.getRequest().getInputStream(), fSet);

                resp.status(200);
            }
        } else {
            HttpReqHelper.sendString(resp.getResponse(), 403, "text/plain", "Forbidden");
        }
    }

    private LoginUser authorizeUser(JettyEmbeddedRequest req, JettyEmbeddedResponse resp) throws Exception {
        String auth = req.getRequest().getHeader("Authorization");
        
        var iterHdr = req.getRequest().getHeaderNames();
        
        while (iterHdr.hasMoreElements()) {
            _log.info("Header: {}", iterHdr.nextElement());
        }
        
        if (auth == null) {
            HttpReqHelper.sendString(resp.getResponse(), 401, "text/plain", "Unauthorized");
            
            return null;
        }

        String[] authSplit = auth.split(" ");

        if ("Basic".equals(authSplit[0])) {
            String sUserPass = new String(Base64.getDecoder().decode(authSplit[1]));
            String[] splitUser = sUserPass.split(":");

            LoginUser lu = _users.get(splitUser[0]);

            if (lu != null) {
                if (lu.getPassword().equals(splitUser[1])) {
                    return lu;
                } else {
                    HttpReqHelper.sendString(resp.getResponse(), 403, "text/plain", "Forbidden");
                    return null;
                }
            } else {
                HttpReqHelper.sendString(resp.getResponse(), 403, "text/plain", "Forbidden");
                return null;
            }
        } else {
            HttpReqHelper.sendString(resp.getResponse(), 403, "text/plain", "Forbidden");
            return null;
        }
    }

    private static final String FILE_ROW = """
                                           <tr>
                                              <td><a href="%s">%s</td>
                                              <td>%s</td>
                                              <td>%s</td>
                                           </tr>
                                           """;

    private void processRepoGetHead(JettyEmbeddedRequest req, JettyEmbeddedResponse resp) throws Exception {
        _log.info("This is a test");

        String strLoc = req.getRequestInfo().getString("fileLoc");

        _log.info("GET Path: {}", strLoc);

        // Replace any .. parent tags
        strLoc = strLoc.replace("/..", "");

        Path fSet = Paths.get(_storePath, strLoc);

        try {
            if (Files.notExists(fSet)) {
                _log.error("File " + strLoc + " was not found");
                HttpReqHelper.sendString(resp.getResponse(), 404, "text/plain", "File " + strLoc + " was not found");
            } else if (Files.isDirectory(fSet) && !"HEAD".equals(req.getRequest().getMethod())) {
                String strFullReq = req.getRequestInfo().getString("fileLoc");;

                //strFullReq.replace("http:", "https:");
                if (!strFullReq.endsWith("/")) {
                    strFullReq += "/";
                }

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(fSet)) {
                    StringBuilder sb = new StringBuilder();

                    sb.append("<html>");
                    sb.append("<body>");
                    sb.append("<table>");
                    sb.append("<thead>");
                    sb.append("<tr>");
                    sb.append("<th>File Name</th>");
                    sb.append("<th>Last Modified</th>");
                    sb.append("<th>Size</th>");
                    sb.append("</tr></thead>");
                    sb.append("<tbody>");

                    for (Path file : stream) {
                        sb.append(String.format(FILE_ROW, strFullReq + file.getFileName(), file.getFileName(), Files.getLastModifiedTime(file), Files.size(file)));
                    }

                    sb.append("</tbody>");
                    sb.append("</body>");
                    sb.append("</html>");

                    HttpReqHelper.sendString(resp.getResponse(), 200, "text/html", sb.toString());
                }
            } else {
                String contentType = Files.probeContentType(fSet);

                if (contentType == null) {
                    contentType = "plain/text";
                }

                resp.getResponse().setHeader("Content-Type", contentType);
                resp.contentLength(Long.valueOf(Files.size(fSet)).intValue());

                if ("GET".equals(req.getRequest().getMethod())) {
                    try (OutputStream os = resp.getResponse().getOutputStream()) {
                        Files.copy(fSet, os);
                    }
                } else if ("HEAD".equals(req.getRequest().getMethod())) {
                    // Nothing really to set here
                }
            }
        } catch (IOException io) {
            // Nothing to do here
        }
    }

}
