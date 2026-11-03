/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.perforce.it;

import org.junit.jupiter.api.Assumptions;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A throwaway Perforce server for one test: {@code p4d} over a temporary root on a free localhost port, plus a
 * {@code p4} command line bound to it.
 * <p>
 * The binaries are looked up in the directory of the {@code P4_TEST_BIN_DIR} environment variable (or the
 * {@code p4.test.bin.dir} system property), then on {@code PATH}. Without them the calling test is skipped.
 */
public final class P4TestServer implements AutoCloseable {
    public static final String USER = "test";
    public static final String CLIENT = "test";

    private final File myP4;
    private final Path myServerRoot;
    private final String myPort;
    private final Process myProcess;

    private P4TestServer(File p4, File p4d, Path serverRoot) throws IOException, InterruptedException {
        myP4 = p4;
        myServerRoot = serverRoot;
        myPort = "localhost:" + freePort();
        ProcessBuilder builder = new ProcessBuilder(p4d.getPath(), "-r", serverRoot.toString(), "-p", myPort, "-L", "log", "-J", "journal");
        builder.directory(serverRoot.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(serverRoot.resolve("p4d.out").toFile());
        myProcess = builder.start();
        awaitStarted();
    }

    /**
     * Starts a server under {@code serverRoot}; skips the test when no {@code p4}/{@code p4d} binaries are available.
     */
    public static P4TestServer start(Path serverRoot) throws IOException, InterruptedException {
        File p4 = findBinary("p4");
        File p4d = findBinary("p4d");
        Assumptions.assumeTrue(p4 != null && p4d != null,
            "needs p4 and p4d binaries: set P4_TEST_BIN_DIR or put them on PATH");
        Files.createDirectories(serverRoot);
        return new P4TestServer(p4, p4d, serverRoot);
    }

    public static File findBinary(String name) {
        List<String> dirs = new ArrayList<>();
        String env = System.getenv("P4_TEST_BIN_DIR");
        if (env != null) {
            dirs.add(env);
        }
        String property = System.getProperty("p4.test.bin.dir");
        if (property != null) {
            dirs.add(property);
        }
        String path = System.getenv("PATH");
        if (path != null) {
            dirs.addAll(Arrays.asList(path.split(File.pathSeparator)));
        }
        for (String dir : dirs) {
            File file = new File(dir, name);
            if (file.isFile() && file.canExecute()) {
                return file;
            }
        }
        return null;
    }

    public String getPort() {
        return myPort;
    }

    public File getP4() {
        return myP4;
    }

    /**
     * Runs {@code p4 -p port -u test -c test args...} in {@code workDir}, feeding {@code input} to stdin, and returns stdout.
     * Fails on a non-zero exit code.
     */
    public String p4(Path workDir, @org.jspecify.annotations.Nullable String input, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(myP4.getPath(), "-p", myPort, "-u", USER, "-c", CLIENT));
        command.addAll(Arrays.asList(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        Map<String, String> env = builder.environment();
        // p4 resolves relative file arguments against PWD, not against the process working directory
        env.put("PWD", workDir.toString());
        env.remove("P4CONFIG");
        env.remove("P4PORT");
        env.remove("P4CLIENT");
        env.remove("P4USER");
        env.remove("P4PASSWD");
        env.remove("P4TICKETS");
        Process process = builder.start();
        if (input != null) {
            process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        }
        process.getOutputStream().close();
        byte[] stdout = process.getInputStream().readAllBytes();
        byte[] stderr = process.getErrorStream().readAllBytes();
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IOException("p4 " + String.join(" ", args) + " failed: " + new String(stderr, StandardCharsets.UTF_8)
                + new String(stdout, StandardCharsets.UTF_8));
        }
        return new String(stdout, StandardCharsets.UTF_8);
    }

    /**
     * Creates the {@code test} client with the given root, mapping the whole depot.
     */
    public void createClient(Path root) throws IOException, InterruptedException {
        String spec = "Client: " + CLIENT + "\n\nRoot: " + root + "\n\nOptions: noallwrite noclobber nocompress unlocked nomodtime normdir\n\n"
            + "LineEnd: local\n\nView:\n\t//depot/... //" + CLIENT + "/...\n";
        p4(root, spec, "client", "-i");
    }

    private void awaitStarted() throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        IOException last = null;
        while (System.currentTimeMillis() < deadline) {
            if (!myProcess.isAlive()) {
                throw new IOException("p4d exited with " + myProcess.exitValue() + ": "
                    + Files.readString(myServerRoot.resolve("p4d.out")));
            }
            try {
                p4(myServerRoot, null, "info");
                return;
            }
            catch (IOException e) {
                last = e;
                Thread.sleep(200);
            }
        }
        throw new IOException("p4d did not start on " + myPort, last);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Override
    public void close() throws InterruptedException {
        try {
            p4(myServerRoot, null, "admin", "stop");
        }
        catch (IOException ignored) {
        }
        if (!myProcess.waitFor(10, TimeUnit.SECONDS)) {
            myProcess.destroyForcibly();
            myProcess.waitFor(10, TimeUnit.SECONDS);
        }
    }
}
