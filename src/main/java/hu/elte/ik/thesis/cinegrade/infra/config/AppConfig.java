package hu.elte.ik.thesis.cinegrade.infra.config;

import hu.elte.ik.thesis.cinegrade.domain.enums.Theme;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

@Getter
public enum AppConfig {
    INSTANCE;

    private final String appName;
    private final String appVersion;
    private final String appDirectory;
    private final String projectDirectory;
    private final String logDirectory;
    private final String presetDirectory;
    private final String tempDirectory;
    private final String javaVersion;
    private final Theme fallBackTheme;
    private final long requiredSpace;
    private final boolean developmentMode = false;
    private final String appApi;
    private final String appUrl;

    AppConfig() {
        Properties properties = new Properties();
        try (InputStream in = getPackagedConfigFile()) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            Logger logger = LogManager.getLogger(AppConfig.class);
            logger.debug("Could not load app.properties: " + e.getMessage());
        }

        // boolean isPackaged = System.getProperty("jpackage.app-destination") != null;
        // String env = System.getProperty("app.env", properties.getProperty("app.env", "development"));
        // this.developmentMode = !isPackaged && "development".equalsIgnoreCase(env);

        this.appName = properties.getProperty("app.name", "cinegrade");
        this.appVersion = properties.getProperty("app.version", "1.0-SNAPSHOT");
        this.appDirectory = Paths.get(System.getProperty("user.home"), "." + this.appName).toString();
        this.logDirectory = Paths.get(this.appDirectory, "logs").toString();
        this.projectDirectory = Paths.get(this.appDirectory, "projects").toString();
        this.presetDirectory = Paths.get(this.appDirectory, "presets").toString();
        this.tempDirectory = Paths.get(this.appDirectory, "temp").toString();
        this.javaVersion = properties.getProperty("app.java.version", System.getProperty("java.version"));
        this.fallBackTheme = Theme.DARK;
        this.requiredSpace = 5L * 1024L * 1024L * 1024L;
        this.appApi = properties.getProperty("app.api", "http://localhost:8000/api/v1");
        this.appUrl = properties.getProperty("app.url", "http://localhost:8000");
    }

    public String getRootDirectory() {
        return appDirectory;
    }

    public InputStream getPackagedConfigFile() {
        if (!developmentMode) {
            String appPath = System.getProperty("jpackage.app-destination");
            if (appPath != null) {

                // In jpackage: <install_dir>/CineGrade.exe -> <install_dir>/app/CineGrade.cfg
                Path exePath = Path.of(appPath);
                Path cfgPath = exePath.getParent().resolve("app").resolve("CineGrade.cfg");
                if (Files.exists(cfgPath)) {
                    try {
                        return Files.newInputStream(cfgPath);
                    } catch (IOException e) {
                        LogManager.getLogger(AppConfig.class).debug("Could not read packaged config file: " + e.getMessage());
                    }
                }
            }
        }
        return getClass().getClassLoader().getResourceAsStream("app.properties");
    }
}