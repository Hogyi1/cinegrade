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
    private final boolean developmentMode;
    private final String appApi;
    private final String appUrl;

    AppConfig() {
        Properties properties = new Properties();
        try (InputStream in = getPackagedConfigFile()) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            System.out.println("Could not load app.config: " + e.getMessage());
        }

        this.developmentMode = properties.getProperty("app.env", "dev").equalsIgnoreCase("dev");

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
        return getClass().getClassLoader().getResourceAsStream("app.config");
    }
}