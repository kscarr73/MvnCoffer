package com.progbits.mvn.coffer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Servlet for the Project
 *
 * @author scarr
 */
@Component(name = "MvnCofferServlet", 
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        service = { HttpServlet.class }, 
        property = {"name=MvnCofferServlet", "alias=/coffer"})
public class MvnCofferServlet extends HttpServlet {

    private Logger _log = LoggerFactory.getLogger(MvnCofferServlet.class);

    private Map<String, LoginUser> _users = new HashMap<>();

    private String _storePath = System.getProperty("karaf.base") + "/data/coffer";
    private Path _storage;

    public String getStorePath() {
        return _storePath;
    }

    public void setStorePath(String _storePath) {
        this._storePath = _storePath;
    }

    @Activate
    public void setup(Map<String, String> config) {
        update(config);
    }

    @Modified
    public void update(Map<String, String> config) {
        _storePath = config.get("repoDir");

        _storePath = _storePath.replace("${karaf.base}", System.getProperty("karaf.base"));

        for (Map.Entry<String, String> entry : config.entrySet()) {
            if (entry.getKey().startsWith("user_")) {
                String user = entry.getKey().replace("user_", "");

                String[] sPassRoles = entry.getValue().split(";");

                _users.put(user, new LoginUser(user, sPassRoles[0], sPassRoles[1]));
            }
        }
    }

    @Override
    public void init() throws ServletException {
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
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        switch (req.getMethod()) {
            case "GET":
                if (req.getPathInfo().startsWith("/repository")) {
                    processRepoGet(req, resp);
                }
                break;

            case "POST":
            case "PUT":
                String auth = req.getHeader("Authorization");

                if (auth != null) {
                    LoginUser lu = authorizeUser(auth);

                    if (lu != null) {
                        try {
                            processRepoSave(req, resp, lu);
                        } catch (Exception ex) {
                            _log.error("save File", ex);
                        }
                    } else {
                        resp.sendError(403);
                    }
                } else {
                    resp.sendError(401);
                }

                break;

            default:
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method Not Allowed");
                break;
        }
    }

    private void processRepoSave(HttpServletRequest req, HttpServletResponse resp, LoginUser lu) throws Exception {
        String strLoc = req.getPathInfo().replace("/repository", "");
        Path fSet = Paths.get(_storePath, strLoc);

        String[] sSplitPaths = strLoc.split("/");

        if (lu.hasRole(sSplitPaths[1] + "_WRITE")) {
            if ((strLoc.contains(".jar") || strLoc.contains(".pom")) && Files.exists(fSet)) {
                resp.sendError(409, "Overwriting Release Not Allowed");
            } else {
                Files.createDirectories(fSet.getParent());

                Files.copy(req.getInputStream(), fSet);

                resp.setStatus(200);
            }
        } else {
            resp.sendError(403);
        }

    }

    private LoginUser authorizeUser(String auth) {
        String[] authSplit = auth.split(" ");

        if ("Basic".equals(authSplit[0])) {
            String sUserPass = new String(Base64.getDecoder().decode(authSplit[1]));
            String[] splitUser = sUserPass.split(":");

            LoginUser lu = _users.get(splitUser[0]);

            if (lu != null) {
                if (lu.getPassword().equals(splitUser[1])) {
                    return lu;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void processRepoGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String strLoc = req.getPathInfo().replace("/repository", "");

        // Replace any .. parent tags
        strLoc = strLoc.replace("/..", "");

        Path fSet = Paths.get(_storePath, strLoc);

        if (Files.notExists(fSet)) {
            _log.error("File " + strLoc + " was not found");
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "File " + strLoc + " was not found");
        } else if (Files.isDirectory(fSet)) {
            String strFullReq = req.getRequestURL().toString();

            if (!strFullReq.endsWith("/")) {
                strFullReq += "/";
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(fSet)) {
                resp.getWriter().append("<html>");
                resp.getWriter().append("<body>");
                resp.getWriter().append("<table>");
                resp.getWriter().append("<thead>");
                resp.getWriter().append("<tr>");
                resp.getWriter().append("<th>File Name</th>");
                resp.getWriter().append("<th>Last Modified</th>");
                resp.getWriter().append("<th>Size</th>");
                resp.getWriter().append("</tr></thead>");
                resp.getWriter().append("<tbody>");

                for (Path file : stream) {
                    resp.getWriter().append("<tr>");
                    resp.getWriter().append("<td><a href=\""
                            + strFullReq + file.getFileName() + "\">" + file.getFileName() + "</a></td>");
                    resp.getWriter().append("<td>" + Files.getLastModifiedTime(file) + "</td>");
                    resp.getWriter().append("<td>" + Files.size(file) + "</td>");

                    resp.getWriter().append("</tr>");

                }

                resp.getWriter().append("</tbody>");
                resp.getWriter().append("</body>");
                resp.getWriter().append("</html>");
            }
        } else {
            InputStream is;

            resp.setContentType(Files.probeContentType(fSet));
            resp.setContentLength(Long.valueOf(Files.size(fSet)).intValue());

            Files.copy(fSet, resp.getOutputStream());
        }
    }

}
