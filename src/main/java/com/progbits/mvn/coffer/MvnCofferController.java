package com.progbits.mvn.coffer;

import io.helidon.common.http.Http;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import java.io.IOException;
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
public class MvnCofferController implements HttpService {

    private Logger _log = LogManager.getLogger(MvnCofferController.class);

    private static final String ALIAS = "/coffer/repository";
    
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
        update();
        init();
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

    private void init() {
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
    public void routing(HttpRules httpRules) {
        httpRules
                .get("/coffer/healthcheck", this::healthCheck)
                .head(ALIAS + "*", this::processRepoGetHead)
                .get(ALIAS + "*", this::processRepoGetHead)
                .post(ALIAS + "*", this::processRepoSave)
                .put(ALIAS + "*", this::processRepoSave);
    }

    private void healthCheck(ServerRequest req, ServerResponse resp) {
        resp.status(Http.Status.OK_200);
        resp.send("Ok");
    }
    
    private void processRepoSave(ServerRequest req, ServerResponse resp) throws Exception {
        LoginUser lu = authorizeUser(req, resp);

        if (lu == null) {
            return;
        }

        String strLoc = req.path().path().replace(ALIAS, "");
        Path fSet = Paths.get(_storePath, strLoc);

        String[] sSplitPaths = strLoc.split("/");

        if (lu.hasRole(sSplitPaths[1] + "_WRITE")) {
            if (!strLoc.contains(".xml") && Files.exists(fSet)) {
                resp.status(Http.Status.CONFLICT_409);
                resp.send("Overwriting Release Not Allowed");
            } else {
                if (strLoc.contains("maven-metadata.xml") && Files.exists(fSet)) {
                    Files.delete(fSet);
                }

                Files.createDirectories(fSet.getParent());

                Files.copy(req.content().inputStream(), fSet);

                resp.status(Http.Status.OK_200);
                resp.send();
            }
        } else {
            resp.status(Http.Status.FORBIDDEN_403);
            resp.send("Forbidden");
        }
    }

    private LoginUser authorizeUser(ServerRequest req, ServerResponse resp) {
        Http.HeaderValue authHdr = req.headers().get(Http.Header.createName("authorization", "Authorization"));

        if (authHdr.value() == null) {
            resp.status(Http.Status.UNAUTHORIZED_401);
            resp.send("UnAuthorized");
            return null;
        }

        String[] authSplit = authHdr.value().split(" ");

        if ("Basic".equals(authSplit[0])) {
            String sUserPass = new String(Base64.getDecoder().decode(authSplit[1]));
            String[] splitUser = sUserPass.split(":");

            LoginUser lu = _users.get(splitUser[0]);

            if (lu != null) {
                if (lu.getPassword().equals(splitUser[1])) {
                    return lu;
                } else {
                    resp.status(Http.Status.FORBIDDEN_403);
                    resp.send("Forbidden");
                    return null;
                }
            } else {
                resp.status(Http.Status.FORBIDDEN_403);
                resp.send("Forbidden");
                return null;
            }
        } else {
            resp.status(Http.Status.FORBIDDEN_403);
            resp.send("Forbidden");
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
    private void processRepoGetHead(ServerRequest req, ServerResponse resp) {
        _log.info("This is a test");
        
        String strLoc = req.path().path().replace(ALIAS, "");

        // Replace any .. parent tags
        strLoc = strLoc.replace("/..", "");

        Path fSet = Paths.get(_storePath, strLoc);
        try {
            if (Files.notExists(fSet)) {
                _log.error("File " + strLoc + " was not found");
                resp.status(Http.Status.NOT_FOUND_404);
                resp.send("File " + strLoc + " was not found");
            } else if (Files.isDirectory(fSet) && !"HEAD".equals(req.prologue().method().text())) {
                String strFullReq = req.path().path();

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
                    
                    resp.header("Content-Type", "text/html");
                    resp.send(sb.toString());
                }
            } else {
                resp.header("Content-Type", Files.probeContentType(fSet));
                resp.contentLength(Long.valueOf(Files.size(fSet)).intValue());

                if ("GET".equals(req.prologue().method().text())) {
                    Files.copy(fSet, resp.outputStream());
                    resp.send();
                }
            }
        } catch (IOException io) {

        }
    }

}
