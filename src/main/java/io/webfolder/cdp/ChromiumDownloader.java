/**
 * cdp4j - Chrome DevTools Protocol for Java
 * Copyright © 2017, 2018 WebFolder OÜ (support@webfolder.io)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.webfolder.cdp;

import static java.io.File.pathSeparator;
import static java.lang.Math.round;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.lang.System.getProperty;
import static java.lang.Thread.sleep;
import static java.nio.file.FileSystems.newFileSystem;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.getPosixFilePermissions;
import static java.nio.file.Files.isExecutable;
import static java.nio.file.Files.setPosixFilePermissions;
import static java.nio.file.Files.size;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.Paths.get;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.util.Locale.ENGLISH;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.webfolder.cdp.exception.CdpException;
import io.webfolder.cdp.logger.CdpLogger;
import io.webfolder.cdp.logger.CdpLoggerFactory;
import io.webfolder.cdp.logger.CdpLoggerType;
import io.webfolder.cdp.logger.LoggerFactory;

public class ChromiumDownloader implements Downloader {

    private static final String OS                     = getProperty("os.name").toLowerCase(ENGLISH);

    private static final boolean WINDOWS               = ";".equals(pathSeparator);

    private static final boolean MAC                   = OS.contains("mac");

    private static final boolean LINUX                 = OS.contains("linux");

    private static final String DOWNLOAD_HOST          = "https://storage.googleapis.com/chromium-browser-snapshots";

    private static final int TIMEOUT                   = 10 * 1000; // 10 seconds

    private final CdpLogger logger;

    private static class ZipVisitor extends SimpleFileVisitor<Path> {

        private final Path sourceRoot;

        private final Path destinationRoot;

        public ZipVisitor(Path sourceRoot, Path destinationRoot) {
            this.sourceRoot = sourceRoot;
            this.destinationRoot = destinationRoot;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path sourceDirectory, BasicFileAttributes attrs) throws IOException {
            if (sourceRoot.equals(sourceDirectory)) {
                return CONTINUE;
            }
            if (sourceDirectory.getNameCount() == 1) {
                return CONTINUE;
            }
            String sourcePath = sourceDirectory.subpath(1, sourceDirectory.getNameCount()).toString();
            Path destinationDirectory = destinationRoot.resolve(sourcePath);
            createDirectories(destinationDirectory);
            return CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException {
            String sourcePath = sourceFile.subpath(1, sourceFile.getNameCount()).toString();
            Path destinationFile = destinationRoot.resolve(sourcePath);
            if (exists(destinationFile)) {
                delete(destinationFile);
            }
            copy(sourceFile, destinationFile);
            return CONTINUE;
        }
    }

    public ChromiumDownloader() {
        this(new CdpLoggerFactory());
    }

    public ChromiumDownloader(LoggerFactory loggerFactory) {
        this.logger = loggerFactory.getLogger("cdp4j.downloader");
    }

    @Override
    public Path download() {
        return download(getLatestVersion());
    }

    private static ChromiumVersion getLatestVersion() {
        String url = DOWNLOAD_HOST;

        if ( WINDOWS ) {
            url += "/Win_x64/LAST_CHANGE";
        } else if ( LINUX ) {
            url += "/Linux_x64/LAST_CHANGE";
        } else if ( MAC ) {
            url += "/Mac/LAST_CHANGE";
        } else {
            throw new CdpException("Unsupported OS found - " + OS);
        }

        try {
            URL u = new URL(url);

            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);

            if ( conn.getResponseCode() != 200 ) {
                throw new CdpException(conn.getResponseCode() + " - " + conn.getResponseMessage());
            }

            String result = null;
            try (Scanner s = new Scanner(conn.getInputStream())) {
                s.useDelimiter("\\A");
                result = s.hasNext() ? s.next() : "";
            }
            return new ChromiumVersion(Integer.parseInt(result));
        } catch (IOException e) {
            throw new CdpException(e);
        }
    }

    public Path download(ChromiumVersion version) {
        Path destinationRoot = get(getProperty("user.home"))
                .resolve(".cdp4j")
                .resolve("chromium-" + valueOf(version.revision));

        Path executable = destinationRoot.resolve("chrome.exe");
        if ( LINUX ) {
            executable = destinationRoot.resolve("chrome");
        } else if ( MAC ) {
            executable = destinationRoot.resolve("Chromium.app/Contents/MacOS/Chromium");
        }

        if (exists(executable) && isExecutable(executable)) {
            return executable;
        }

        String url;

        if ( WINDOWS ) {
            url = format("%s/Win_x64/%d/chrome-win32.zip", DOWNLOAD_HOST, version.revision);
        } else if ( LINUX ) {
            url = format("%s/Linux_x64/%d/chrome-linux.zip", DOWNLOAD_HOST, version.revision);
        } else if ( MAC ) {
            url = format("%s/Mac/%d/chrome-mac.zip", DOWNLOAD_HOST, version.revision);
        } else {
            throw new CdpException("Unsupported OS found - " + OS);
        }

        try {
            URL u = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            if ( conn.getResponseCode() != 200 ) {
                throw new CdpException(conn.getResponseCode() + " - " + conn.getResponseMessage());
            }
            long contentLength = conn.getHeaderFieldLong("x-goog-stored-content-length", 0);
            String fileName = url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf(".")) + "-r" + version.revision + ".zip";
            Path archive = get(getProperty("java.io.tmpdir")).resolve(fileName);
            if ( exists(archive) && contentLength != size(archive) ) {
                delete(archive);
            }
            if ( ! exists(archive) ) {
                logger.info("Downloading Chromium [revision=" + version.revision + "] 0%");
                u = new URL(url);
                if ( conn.getResponseCode() != 200 ) {
                    throw new CdpException(conn.getResponseCode() + " - " + conn.getResponseMessage());
                }
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                Thread thread = null;
                AtomicBoolean halt = new AtomicBoolean(false);
                Runnable progress = () -> {
                    try {
                        long fileSize = size(archive);
                        logger.info("Downloading Chromium [revision={}] {}%",
                                version.revision, round((fileSize * 100L) / contentLength));
                    } catch (IOException e) {
                        // ignore
                    }
                };
                try (InputStream is = conn.getInputStream()) {
                    logger.info("Download location: " + archive.toString());
                    thread = new Thread(() -> {
                        while (true) {
                            try {
                                if (halt.get()) {
                                    break;
                                }
                                progress.run();
                                sleep(1000);
                            } catch (Throwable e) {
                                // ignore
                            }
                        }
                    });
                    thread.setName("cdp4j");
                    thread.setDaemon(true);
                    thread.start();
                    copy(conn.getInputStream(), archive);
                } finally {
                    if ( thread != null ) {
                        progress.run();
                        halt.set(true);
                    }
                }
            }
            logger.info("Extracting to: " + destinationRoot.toString());
            if (exists(archive)) {
                createDirectories(destinationRoot);
                try (FileSystem fileSystem = newFileSystem(archive, null)) {
                    Iterator<Path> iter = fileSystem.getRootDirectories().iterator();
                    if (iter.hasNext()) {
                        Path sourceRoot = iter.next();
                        walkFileTree(sourceRoot, new ZipVisitor(sourceRoot, destinationRoot));
                    }
                }
            }
            if ( ! WINDOWS ) {
                Set<PosixFilePermission> permissions = getPosixFilePermissions(executable);
                if ( ! permissions.contains(OWNER_EXECUTE)) {
                    permissions.add(OWNER_EXECUTE);
                    setPosixFilePermissions(executable, permissions);
                }
                if ( ! permissions.contains(GROUP_EXECUTE) ) {
                    permissions.add(GROUP_EXECUTE);
                    setPosixFilePermissions(executable, permissions);
                }
            }
        } catch (IOException e) {
            throw new CdpException(e);
        }
        return executable;
    }

    public static void main(String[] args) {
        Path download = new ChromiumDownloader(new CdpLoggerFactory(CdpLoggerType.Console)).download();
        System.out.println(download);
    }
}
