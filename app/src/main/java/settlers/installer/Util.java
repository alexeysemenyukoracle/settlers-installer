/*
 */
package settlers.installer;

import com.owlike.genson.Genson;
import com.owlike.genson.GensonBuilder;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.swing.filechooser.FileSystemView;
import net.sf.fikin.ant.EmbeddedAntProject;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tools.ant.Project;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;
import settlers.installer.model.GameVersion;

/**
 * Class with lots of utility functions that are taken out of App.
 * Windows:
 * <pre>
 * reg EXPORT "HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\s3.exe" test.key
 * </pre>
 * Windows Registry Editor Version 5.00
 * 
 * <pre>
 * [HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\s3.exe]
 * "Path"="f:/Ubisoft/Ubisoft Game Launcher/games/thesettlers3\\"
 * "@"="f:/Ubisoft/Ubisoft Game Launcher/games/thesettlers3\\Siedler3R.exe"
 * </pre>
 * @author hiran
 */
public class Util {
    private static final Logger log = LogManager.getLogger(Util.class);
    private static final Logger logEnv = LogManager.getLogger("settlers.installer.env");
    
    public static final String RELEASE_URL = "https://api.github.com/repos/paulwedeck/settlers-remake/releases";
    public static final String WORKFLOW_RUNS_URL = "https://api.github.com/repos/paulwedeck/settlers-remake/actions/runs";
    public static final String GITHUB_REPO_NAME = "paulwedeck/settlers-remake";
    public static final String GITHUB_REPO_NAME_ISSUES = 
            "paulwedeck/settlers-remake";
            //"HiranChaudhuri/settlers-installer";
    
    private static final int GITHUB_MIN_LIMIT4BROWSING = 10;
    
    /** 
     * Creates a Genson parser that treats timestamps as java.util.Date.
     *
     * @return the parser
     */
    public static Genson getGenson() {
        return new GensonBuilder()
                .useDateAsTimestamp(true)
                .useDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"))
                .create();
    }
    
    /**
     * Sorts the list by publishing date.
     * 
     * @param releases the list to be sorted
     * @return the sorted list
     */
    public static List<GHRelease> sortReleaseByDate(List<GHRelease> releases) {
        List<GHRelease> result = new ArrayList<>(releases);
        Collections.sort(result, new Comparator<GHRelease>() {
            @Override
            public int compare(GHRelease t1, GHRelease t) {
                if (t.getPublished_at()==null) {
                    return 1;
                }
                return t.getPublished_at().compareTo(t1.getPublished_at());
            }
        });
        return result;
    }

    /**
     * Sorts the list by publishing date.
     * 
     * @param games the list to be sorted
     * @return the sorted list
     */
    public static List<GameVersion> sortGamesByDate(List<GameVersion> games) {
        List<GameVersion> result = new ArrayList<>(games);
        Collections.sort(result, new Comparator<GameVersion>() {
            @Override
            public int compare(GameVersion t1, GameVersion t) {
                if (t.getPublishedAt()==null) {
                    return 1;
                }
                return t.getPublishedAt().compareTo(t1.getPublishedAt());
            }
        });
        return result;
    }
    
    /**
     * Sorts the list by publishing date.
     * 
     * @param artifacts the list to be sorted
     * @return the sorted list
     */
    public static List<GHArtifact> sortArtifactsByDate(List<GHArtifact> artifacts) {
        List<GHArtifact> result = new ArrayList<>(artifacts);
        Collections.sort(result, new Comparator<GHArtifact>() {
            @Override
            public int compare(GHArtifact t1, GHArtifact t) {
                try {
                    if (t.getUpdatedAt()==null) {
                        return 1;
                    }
                    return t.getUpdatedAt().compareTo(t1.getUpdatedAt());
                } catch (IOException e) {
                    log.warn("Coult not compare artifact dates", e);
                    return 0;
                }
            }
        });
        return result;
    }
    
    /**
     * Returns the publishing date for some GHObject.
     * 
     * @param object the object in question
     * @return the publishing data, or what comes near to it
     */
    public static Date getDateFor(GHObject object) {
        Date result = null;
        if (object instanceof GHRelease) {
            result = ((GHRelease)object).getPublished_at();
        } else {
            try {
                result = object.getUpdatedAt();
            } catch (IOException e) {
                log.error("cannot grab updated date for {}", object);
            }
        }
        return result;
    }
    
    /**
     * Sorts a heterogenous list by publishing date.
     * 
     * @param objects the list to be sorted
     * @return the sorted list
     */
    public static List<GHObject> sortGHObjectByDate(List<GHObject> objects) {
        List<GHObject> result = new ArrayList<>(objects);
        Collections.sort(result, new Comparator<GHObject>() {
            @Override
            public int compare(GHObject one, GHObject other) {
                Date dateOne = getDateFor(one);
                Date dateOther = getDateFor(other);
                
                return dateOther.compareTo(dateOne);
            }
        });
        return result;
    }

    /**
     * Sorts the list by publishing date.
     * 
     * @param workflowRuns the list to be sorted
     * @return the sorted list
     */
    public static List<GHWorkflowRun> sortWorkflowByDate(List<GHWorkflowRun> workflowRuns) {
        List<GHWorkflowRun> result = new ArrayList<>(workflowRuns);
        Collections.sort(result, new Comparator<GHWorkflowRun>() {
            @Override
            public int compare(GHWorkflowRun t1, GHWorkflowRun t) {
                try {
                    if (t.getCreatedAt()==null) {
                        return 1;
                    }
                    return t.getCreatedAt().compareTo(t1.getCreatedAt());
                } catch (IOException e) {
                    log.warn("Could not compare workflows", e);
                    return 0;
                }
            }
        });
        return result;
    }

    /** Returns the releases locally installed.
     * The list is sorted by publishing date.
     * 
     * @return the list of releases
     * @throws FileNotFoundException something went wrong
     */
    public static List<GameVersion> getInstalledGames() {
        List<GameVersion> result = new ArrayList<>();
        Genson genson = getGenson();
        
        File gamesFolder = getGamesFolder();
        if (gamesFolder.isDirectory()) {
            for(File game: gamesFolder.listFiles()) {
                File metadata = new File(game, "metadata.json");
                try {
                    GameVersion gv = genson.deserialize(new FileInputStream(metadata), GameVersion.class);
                    result.add(gv);
                } catch (Exception e) {
                    log.info("Could not parse {}", metadata.getAbsolutePath());
                }
            }
        }
        
        return sortGamesByDate(result);
    }

    /**
     * Extracts a ZIP archive.
     * 
     * @param archive The archive to unzip
     * @param target the directory to store it's content
     * @throws IOException something went wrong
     */
    public static void unzip(File archive, File target) throws IOException {
        log.debug("unzip({}, {})", archive, target);
        
        if (!target.exists()) {
            if (!target.mkdirs()) {
                throw new IOException(String.format("Could not create folder %s", target.getAbsolutePath()));
            }
        }
        
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                File destFile = new File(target, entry.getName());
                if (!destFile.toPath().normalize().startsWith(target.toPath())) {
                    log.warn("While unzipping we found {} would expand outside {}", destFile, target);
                    throw new IOException("Bad ZIP entry "+entry.getName());
                }
                if (entry.isDirectory()) {
                    destFile.mkdirs();
                    Files.setLastModifiedTime(destFile.toPath(), entry.getLastModifiedTime());
                } else {
                    Files.copy(zipIn, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Files.setLastModifiedTime(destFile.toPath(), entry.getLastModifiedTime());
                }
                
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
        
        log.debug("unzip done");
    }
    
    /**
     * Extracts a self-extracting ZIP archive (it is actually an EXE).
     * 
     * @param zipfile The archive to unzip
     * @param target the directory to store it's content
     * @throws IOException something went wrong
     * @throws FileNotFoundException something went wrong
     */
    public static void unzipSelfExtractingZip(File zipfile, File target) throws FileNotFoundException, IOException {
        try (ZipInputStream zis = new ZipInputStream(new WinZipInputStream(new FileInputStream(zipfile)))) {
            ZipEntry entry = null;

            while((entry = zis.getNextEntry()) != null){
                File destFile = new File(target, entry.getName());
                if (!destFile.toPath().normalize().startsWith(target.toPath())) {
                    log.warn("While unzipping we found {} would expand outside {}", destFile, target);
                    throw new IOException("Bad ZIP entry "+entry.getName());
                }
                if(entry.isDirectory()){
                    destFile.mkdirs();
                    Files.setLastModifiedTime(destFile.toPath(), entry.getLastModifiedTime());
                } else {
                    destFile.getParentFile().mkdirs();
                    Files.copy(zis, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Files.setLastModifiedTime(destFile.toPath(), entry.getLastModifiedTime());
                }
                zis.closeEntry();
            }
        }
    }
    
    private static void installGeneric(URL url, File target) throws IOException {
        log.debug("installGeneric({}, {})", url, target);
        File tempFolder = getManagedTempFolder();
        tempFolder.mkdirs();

        File f = File.createTempFile("download", ".zip", tempFolder);
        download(url, f);

        int retries = 6;
        boolean done = false;
        while (retries>0 && !done) {
            try {
                unzip(f, target);
                done = true;
            } catch (IOException e) {
                log.info("Could not unzip release. Maybe a virus scanner? Waiting for retry...", e);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    log.debug("Interrupted sleep");
                }
            }

            retries--;
        }
        if (!done) {
            throw new IOException(String.format("Could not unzip %s to %s", f, target));
        }
    }
    
    /**
     * Installs a release locally. The release asset will be downloaded and
     * extracted. Finally the metadata is stored as well.
     * 
     * @param release The release to install
     * @throws IOException something went wrong
     */
    public static void installRelease(GHRelease release) throws IOException {
        log.debug("installRelease({})", release);
        for (PagedIterator<GHAsset> iter = release.listAssets().iterator(); iter.hasNext(); ) {
            GHAsset a = iter.next();
            if ("JSettlers.zip".equals(a.getName())) {
                log.debug("check asset {}", a);
                File target = new File(getGamesFolder(), String.valueOf(release.getId()));
                installGeneric(new URL(a.getBrowserDownloadUrl()), target);
                
                log.debug("writing metadata...");
                File metadata = new File(target, "metadata.json");
                GameVersion gv = new GameVersion();
                gv.setDownloadUrl(a.getBrowserDownloadUrl());
                gv.setInstallPath(target.getAbsolutePath());
                gv.setInstalledAt(new Date());
                gv.setName(release.getName());
                gv.setPublishedAt(release.getPublished_at());
                gv.setBasedOn(release.getClass().getName());
                try (FileOutputStream fos = new FileOutputStream(metadata)) {
                    new Genson().serialize(gv, fos);
                }

                FileTime ft = FileTime.from(release.getPublished_at().toInstant());
                log.debug("setting file time to {}", ft);
                Files.setLastModifiedTime(target.toPath(), ft);

                log.debug("release installed");
                return;
            }
        }
    }
    
    /**
     * Installs the game based on the given workflow run.
     * 
     * @param run the game version to install
     * @throws IOException something went wrong
     */
    public static void installWorkflowRun(GHWorkflowRun run) throws IOException {
        log.debug("installWorkflowRun({})", run);
        
        List<GHArtifact> artifacts = run.listArtifacts().toList();
        for (GHArtifact artifact: artifacts) {
            log.debug("found {}", artifact);
            if ("Release".equals(artifact.getName())) {
                File tempfile = File.createTempFile("artifact", ".zip", getManagedTempFolder());
                File tempfilex = Files.createTempDirectory(getManagedTempFolder().toPath(), "artifactx").toFile();
                File target = new File(Util.getGamesFolder(), String.valueOf(run.getId()));
                log.debug("downloading artifact to {}", tempfile);

                //installGeneric(artifact.getArchiveDownloadUrl(), target);
                artifact.download(is -> {
                    Files.copy(is, tempfile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return null;
                });

                log.debug("extracting to {}", tempfilex);
                unzip(tempfile, tempfilex);
                unzip(new File(tempfilex, "JSettlers.zip"), target);
                
                log.debug("writing metadata...");
                File metadata = new File(target, "metadata.json");
                GameVersion gv = new GameVersion();
                gv.setDownloadUrl(String.valueOf(artifact.getArchiveDownloadUrl()));
                gv.setInstallPath(target.getAbsolutePath());
                gv.setInstalledAt(new Date());
                gv.setName(run.getName()+" "+run.getHeadBranch()+" "+run.getRunNumber());
                gv.setPublishedAt(artifact.getUpdatedAt());
                gv.setBasedOn(run.getClass().getName());
                try (FileOutputStream fos = new FileOutputStream(metadata)) {
                    new Genson().serialize(gv, fos);
                }

                FileTime ft = FileTime.from(artifact.getUpdatedAt().toInstant());
                log.debug("setting file time to {}", ft);
                Files.setLastModifiedTime(target.toPath(), ft);

                log.debug("release installed");
                return;
            }
        }
    }
    
    /**
     * Installs a release locally. The release asset will be downloaded and
     * extracted. Finally the metadata is stored as well.
     * 
     * @param object The release to install
     * @throws IOException something went wrong
     */
    public static void installGame(GHObject object) throws IOException {
        log.debug("installGame({})", object);

        if (object instanceof GHRelease) {
            installRelease((GHRelease)object);
        } else if (object instanceof GHWorkflowRun) {
            installWorkflowRun((GHWorkflowRun)object);
        } else {
            throw new UnsupportedOperationException("Unknown game type");
        }
//        log.debug("writing metadata...");
//        File metadata = new File(target, "metadata.json");
//        GameVersion gv = new GameVersion();
//        gv.setDownloadUrl(a.getBrowserDownloadUrl());
//        gv.setInstallPath(target.getAbsolutePath());
//        gv.setInstalledAt(new Date());
//        gv.setName(object.getName());
//        gv.setPublishedAt(object.getPublished_at());
//        gv.setBasedOn(object.getClass().getName());
//        try (FileOutputStream fos = new FileOutputStream(metadata)) {
//            new Genson().serialize(gv, fos);
//        }
//
//        FileTime ft = FileTime.from(object.getPublished_at().toInstant());
//        log.debug("setting file time to {}", ft);
//        Files.setLastModifiedTime(target.toPath(), ft);

        log.debug("game installed");
    }
    
    /**
     * Deletes a file/directory recursively.
     * 
     * @param file the file to delete
     */
    public static void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                if (! Files.isSymbolicLink(f.toPath())) {
                    deleteDir(f);
                }
            }
        }
        file.delete();
    }

    /**
     * Removes a release from the games folder.
     * 
     * @param release
     * @throws IOException 
     */
    public static void removeRelease(GHRelease release) throws IOException {
        log.debug("removeRelease({})", release);
        File target = new File(getGamesFolder(), String.valueOf(release.getId()));
        deleteDir(target);
    }
    
    /**
     * Returns the user's home folder.
     * 
     * @return the folder reference
     */
    public static File getHomeFolder() {
        return new File(System.getProperty("user.home"));
    }
    
    /**
     * Returns the parent for all folders managed by settlers-installer.
     * 
     * @return the folder reference
     */
    public static File getManagedJSettlersFolder() {
        return new File(getHomeFolder(), ".jsettlers/managed");
    }
    
    /**
     * Returns the folder for temporary files.
     * 
     * @return the folder reference
     */
    public static File getManagedTempFolder() {
        return new File(getManagedJSettlersFolder(), "temp");
    }

    /**
     * Returns the folder containing the Settlers data files.
     * 
     * @return the folder reference
     */
    public static File getDataFolder() {
        return new File(getManagedJSettlersFolder(), "data");
    }
    
    /**
     * Returns the folder hosting the game installtions.
     * 
     * @return the folder reference
     */
    public static File getGamesFolder() {
        return new File(getManagedJSettlersFolder(), "game");
    }
    
    /** 
     * Folder for savegames and logfiles.
     */
    public static File getVarFolder() {
        return new File(getManagedJSettlersFolder(), "var");
    }
    
    /**
     * Run given jar file from the given game.
     * 
     * @param game the game
     * @param jarname the jar filename, relative from the game's installation directory
     * @throws IOException something went wrong
     * @throws InterruptedException something went wrong
     */
    public static void execGameJar(GHObject game, String jarname) throws IOException, InterruptedException {
        log.debug("runGame({})", game);
        File target = new File(getGamesFolder(), String.valueOf(game.getId()));
        File jarfile = new File(target, jarname);
        
        int rc = execJarFile(jarfile);
        if (rc != 0) {
            throw new IOException("Nonzero exit code " + rc + " after running "+jarfile.getAbsolutePath());
        }
    }
    
    /**
     * Run given jar file from the given game.
     * 
     * @param game the game version
     * @param jarname the jar filename, relative from the game's installation directory
     * @throws IOException something went wrong
     * @throws InterruptedException something went wrong
     */
    public static void execGameJar(GameVersion game, String jarname) throws IOException, InterruptedException {
        log.debug("runGame({})", game);
        File target = new File(game.getInstallPath());
        File jarfile = new File(target, jarname);
        
        int rc = execJarFile(jarfile);
        if (rc != 0) {
            throw new IOException("Nonzero exit code " + rc + " after running "+jarfile.getAbsolutePath());
        }
    }

    /**
     * Runs an executable jar in a separate JVM.
     * 
     * @param jarfile the jar file to run
     * @throws IOException something went wrong
     * @throws InterruptedException something went wrong
     */
    public static int execJarFile(File jarfile) throws IOException, InterruptedException {
        if (jarfile == null) {
            throw new IllegalArgumentException("Cannot execute null jar");
        }
        if (!jarfile.exists()) {
            throw new IOException("Could not find jar: " + jarfile.getAbsolutePath());
        }
        
        File javaHome = new File(System.getProperty("java.home"));
        File java = new File(javaHome, "bin/java"); // may need a tweak on Windows
        if (OsDetector.IS_WINDOWS) {
            java = new File(javaHome, "bin/java.exe");
        }
        
        if (!java.canExecute()) {
            // maybe we are pointing to the JLink provided binaries. Let's fall
            // back to the system-provided java installation
            log.info("it seems {} is not executable, falling back", java.getAbsolutePath());
            java = new File("/usr/bin/java");
            if (OsDetector.IS_WINDOWS) {
                java = new File("java.exe");
            }
        }

        List<String> command = new ArrayList<>();
        command.add(java.getAbsolutePath());
        command.add("-Xmx2G");
        command.add("-Dorg.lwjgl.util.Debug=true");
        command.add("-jar");
        command.add(jarfile.getAbsolutePath());
        command.add("--settlers-folder="+getDataFolder().getAbsolutePath());
        // command.add("--music-playall=true");

        log.info("executing {}", command);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        
        File workingDir = getVarFolder();
        if (!workingDir.isDirectory()) {
            workingDir.mkdirs();
        }
        pb.directory(workingDir);
        
        Process p = pb.start();
        log.info("started JSettlers in pid {}", p.pid());
        p.waitFor();
        int rc = p.exitValue();
        log.info("returned with {}", rc);
        return rc;
    }
    
    /** Install goodies from goodies file.
     * 
     * @param goodiesFile the file to install
     * @throws IOException something went wrong
     */
    public static void addGoodiesToData(File goodiesFile) throws IOException {
        unzip(goodiesFile, getDataFolder());
    }
    
    /**
     * Install the Settlers data form a demo file.
     * 
     * @param demoFile the file to install from
     * @throws IOException something went wrong
     */
    public static void addDemoToData(File demoFile) throws IOException {
        unzipSelfExtractingZip(demoFile, getDataFolder());
    }
    
    /**
     * Runs the given ant file with the given properties.
     * 
     * @param buildFile the ant file
     * @param props the properties
     * @throws MalformedURLException something went wrong
     * @throws IOException somwthing went wrong
     */
    public static void runAnt(File buildFile, Properties props) throws MalformedURLException, IOException {

        URL url = Util.class.getClassLoader().getResource( "S3_Installer.xml" );
        InputStream in = url.openStream();
        EmbeddedAntProject prj = new EmbeddedAntProject( new File("."), "build-1.xml", in );
      
        prj.init();
      
        prj.setMessageLevel( Project.MSG_DEBUG );
      
        for (Object key: props.keySet()) {
            prj.setUserProperty(String.valueOf(key), props.getProperty(String.valueOf(key)));
        }
        String[][] params = new String[][] {
              { "property1", "value1" },
              { "property2", "value2" }
        };
        prj.executeTarget( prj.getDefaultTarget() );

    }
    
    /**
     * Finds the CD mount point in Linux.
     * If several CDROM drives are installed, only the first will be returned.
     * 
     * @return the file object to the CDROM, or null if not found
     */
    private static File getCdMountPointLinux() {
        // we assume to run on Linux
        // read /proc/mounts and scan for iso9660 filesystem
        try (Scanner scanner = new Scanner(new File("/proc/mounts"))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.toLowerCase().contains("iso9660")) {
                    log.debug("line: {}", line);
                    StringTokenizer st = new StringTokenizer(line);
                    st.nextToken();
                    String mountPoint = st.nextToken();
                    log.debug("mount point: {}", mountPoint);
                    
                    return new File(mountPoint);
                }
            }
        } catch (Exception e) {
            log.error("Could not get cd mount point", e);
            return null;
        }
        
        return null;
    }

    /**
     * Finds the CD mount point in Linux.
     * If several CDROM drives are installed, only the first will be returned.
     * 
     * @return the file object to the CDROM, or null if not found
     */
    private static File getCdMountPointWindows() {
        File result = null;
        
        FileSystemView fsv = FileSystemView.getFileSystemView();
        for (File root: fsv.getRoots()) {
            String type = fsv.getSystemTypeDescription(root);
            log.debug("Filesystem {} is {}", root, type);
            
            if (type.toLowerCase().contains("cd") && null == result) {
                result = root;
                log.debug("  seems {} is the CDROM", root);
            }
        }
        return result;
    }
    
    /**
     * Returns the settlers-installer's configuration file.
     * 
     * @return the file
     */
    public static File getConfigurationFile() {
        return new File(Util.getManagedJSettlersFolder(), ".settler-installer.properties");
    }
    
    /**
     * Returns the mount point or the filesystem root of the CDROM drive.
     * If several CDROM drives are installed, only the first will be returned.
     * 
     * @return the file object to the CDROM, or null if not found
     */
    public static File getCdMountPoint() {
        if (OsDetector.IS_UNIX) {
            return getCdMountPointLinux();
        } else if (OsDetector.IS_WINDOWS) {
            return getCdMountPointWindows();
        } else {
            log.warn("Unknown operating system {}", OsDetector.OS);
            return null;
        }
    }
    
    /**
     * Ensures the latest release on Github is also installed locally.
     * Also ensures we have no more than the last 5 releases and cleans up
     * older ones.
     */
    public static void installLatest(GitHub github) throws IOException {
        GHRepository repository = github.getRepository(GITHUB_REPO_NAME);
        List<GHRelease> githubReleases = repository.listReleases().toList();
        List<GameVersion> installedGames = Util.getInstalledGames();

        // install if a newer one is available
        if (installedGames.isEmpty() || installedGames.get(0).getPublishedAt().before(githubReleases.get(0).getPublished_at())) {
            GHRelease latest = githubReleases.get(0);
            log.debug("Installing latest release {}", latest);

            installRelease(latest);
        }
    }
    
    /** Return true if the folder is a Settlers install CD.
     * 
     * @param dir the directory to investigate
     * @return true if an installation CD is found
     */
    public static boolean isInstallCD(File dir) {
        if (!dir.isDirectory())
            return false;
        
        ArrayList<String> requiredFiles = new ArrayList<>();
        requiredFiles.add("autorun.inf");
        requiredFiles.add("s3");
        requiredFiles.add("s3.dat");
        
        File[] entries = dir.listFiles();
        if (entries != null) {
            for (File entry: entries) {
                requiredFiles.remove(entry.getName().toLowerCase());
            }
        }
        
        return requiredFiles.isEmpty();
    }

    /** Return true if the folder is a Settlers 3 data folder.
     * 
     * @param dir the directory to investigate
     * @return true if a game data folder is found
     */
    public static boolean isGameFolder(File dir) {
        if (!dir.isDirectory())
            return false;
        
        ArrayList<String> requiredFiles = new ArrayList<>();
        requiredFiles.add("gfx");
        requiredFiles.add("manual");
        requiredFiles.add("map");
        requiredFiles.add("snd");
        
        File[] entries = dir.listFiles();
        if (entries != null) {
            for (File entry: entries) {
                requiredFiles.remove(entry.getName().toLowerCase());
            }
        }
        
        return requiredFiles.isEmpty();
    }

    /**
     * Installs Settlers data files from the CD in the given mount point.
     * 
     * @param cdrom the mount point
     * @throws IOException something went wrong
     */
    public static void installFromCD(File cdrom) throws IOException {
        Properties props = new Properties();
        props.put("cdrom", cdrom.getAbsolutePath());
        props.put("data", getDataFolder().getAbsolutePath());
        runAnt(new File("src/main/resources/S3_Installer.xml"), props);
    }
    
    /**
     * Dumps the properties to the logfile.
     * 
     * @param props the proerties to dump
     */
    public static void dumpProperties(Properties props) {
        logEnv.debug("Properties:");
        TreeSet<Object> keys = new TreeSet<>(props.keySet());
        for (Object key: keys) {
            logEnv.debug("  {} -> {}", key, props.get(key));
        }
    }

    static void dumpEnvironment() {
        logEnv.debug("Environment:");
        TreeSet<String> keys = new TreeSet<>(System.getenv().keySet());
        for (String key: keys) {
            logEnv.debug("  {} -> {}", key, System.getenv(key));
        }
    }

    /**
     * Returns the operating system's hostname.
     * 
     * @return the hostname
     */
    public static String getHostname() {

        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("win")) {
                return execReadToString("hostname");
            } else if (os.contains("nix") || os.contains("nux") || os.contains("mac os x")) {
                return execReadToString("hostname");
            }
        } catch (Exception e) {
            log.info("Could not get hostname", e);
        }
        return "n/a";
    }

    /**
     * Executes a command and returns it's stdout.
     * 
     * @param execCommand the command to execute
     * @return the stdout output of the executed command
     * @throws IOException something went wrong
     */
    public static String execReadToString(String execCommand) throws IOException {
        try (Scanner s = new Scanner(Runtime.getRuntime().exec(execCommand).getInputStream()).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }
    
    private static void download(URL url, File download) throws IOException {
        log.debug("download({}, {})", url, download);
        try (InputStream in = url.openStream()) {
            Files.copy(in, download.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new IOException(String.format("Could not download %s to %s", url, download), e);
        }
        
        log.debug("stored in {}", download);
    }

    /**
     * Downloads an asset into a temporary file and returns the file.
     * 
     * @param asset the asset to download
     * @return the local file
     * @throws IOException something went wrong
     */
    public static File downloadAsset(GHAsset asset) throws IOException {
        log.debug("downloadAsset({})", asset);
        URL url = new URL(asset.getBrowserDownloadUrl());
        File tempFolder = getManagedTempFolder();
        tempFolder.mkdirs();
        
        File download = File.createTempFile(asset.getName()+"_"+asset.getId(), ".zip", tempFolder);
        return download;
    }
    
    private static List<GHObject> availableGamesCache;
    private static Instant availableGamesCacheExpiry;
    private static Duration availableGamesCacheTTL = Duration.ofMinutes(60);
    
    /**
     * Returns the list of games available on Github.
     * 
     * @param github the githup api client
     * @param releasesOnly true if the scan should just contain releases
     * @return the list of available games on GitHub
     * @throws IOException something went wrong
     */
    public static List<GHObject> getAvailableGames(GitHub github, boolean releasesOnly) throws IOException {
        log.debug("getAvailableGames({}, {})", github, releasesOnly);
        
        if (availableGamesCache==null || (availableGamesCacheExpiry!=null && Instant.now().isAfter(availableGamesCacheExpiry))) {
            // either we have no cache or it is expired. Request new data

            List<GHObject> result = new ArrayList<>();
            GHRepository repository = null;
            
            if (github.getRateLimit().getRemaining()>GITHUB_MIN_LIMIT4BROWSING) {
                repository = github.getRepository(GITHUB_REPO_NAME);
                log.debug("Listing releases...");
                result.addAll(repository.listReleases().toList());
                log.debug("Found {} releases", result.size());
            }

            if (!releasesOnly && github.getRateLimit().getRemaining()>GITHUB_MIN_LIMIT4BROWSING) {
                int limit = 30; // do not fetch more than this much runs
                
                //result.addAll(repository.listArtifacts().toList());
                log.debug("Listing workflow runs...");
                List<GHWorkflow> workflows = repository.listWorkflows().toList();
                if (github.getRateLimit().getRemaining()>GITHUB_MIN_LIMIT4BROWSING) {
                    for (GHWorkflow workflow: workflows) {
                        if (github.getRateLimit().getRemaining()>GITHUB_MIN_LIMIT4BROWSING) {
                            for (GHWorkflowRun run: workflow.listRuns().toList()) {
                                if (github.getRateLimit().getRemaining()>GITHUB_MIN_LIMIT4BROWSING) {
                                    if (!run.listArtifacts().toList().isEmpty()) {
                                        result.add(run);
                                        limit--;
                                    }
                                }
                                
                                if (limit <= 0) {
                                    break;
                                }
                            }
                        }

                        if (limit <= 0) {
                            break;
                        }
                    }
                }
            }
            
            log.debug("Found {} games", result.size());
            availableGamesCache = sortGHObjectByDate(result);
            availableGamesCacheExpiry = Instant.now().plus(availableGamesCacheTTL);
        }
        return availableGamesCache;
    }
    
    /**
     * Checks if the specified Github Object resembling a game is installed locally.
     * 
     * @param object the game
     * @return true if it is installed, false otherwise
     */
    public static boolean isInstalled(GHObject object) {
        File target = new File(Util.getGamesFolder(), String.valueOf(object.getId()));
        return target.isDirectory();
    }
    
    /**
     * Returns the size of the desktop (not just one screen).
     * Inspired by https://stackoverflow.com/questions/1936547/java-fullscreen-over-multiple-monitors
     */
    public static Rectangle2D getDesktopSize() {
        Rectangle2D result = new Rectangle2D.Double();
        GraphicsEnvironment localGE = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice gd : localGE.getScreenDevices()) {
          for (GraphicsConfiguration graphicsConfiguration : gd.getConfigurations()) {
            result.union(result, graphicsConfiguration.getBounds(), result);
          }
        }
        return result;
    }
    
    private static Properties getUnixWindowProperties(String windowId) throws IOException {
        Properties result = new Properties();
        
        String windowprops = execReadToString("xwininfo -id "+windowId);
        log.trace("windowprops {}", windowprops);
        StringTokenizer st = new StringTokenizer(windowprops, "\n");
        while (st.hasMoreTokens()) {
            String line = st.nextToken().trim();
            String[] parts = line.split(":");
            if (parts.length==2) {
                String name = parts[0].trim();
                String value = parts[1].trim();
                result.setProperty(name, value);
            }
        }
        
        return result;
    }
    
    /**
     * Returns the recommended screenshot capture region.
     * This function tries to identify the JSettlers window. If it fails, the whole
     * desktop will be returned.
     * 
     * @return the screen capture size
     */
    public static Rectangle2D getCaptureSize() {
        log.debug("getCaptureSize()");
        try {
            if (OsDetector.IS_UNIX) {
                String windowlist = execReadToString("wmctrl -lx");
                StringTokenizer st1 = new StringTokenizer(windowlist, "\n");
                while (st1.hasMoreTokens()) {
                    String window = st1.nextToken();
                    if (window.contains("jsettlers-main-swing-SwingManagedJSettlers.jsettlers-main-swing-SwingManagedJSettlers")
                            || window.contains("jsettlers-ToolsMain.jsettlers-ToolsMain")
                            || window.contains("jsettlers-mapcreator-main-MapCreatorApp")
                            ) {
                        log.trace("window: {}", window);
                        String window_id = window.contains(" ") ? window.split(" ")[0] : window;
                        log.trace("window id: {}", window_id);
                        Properties props = getUnixWindowProperties(window_id);
                        log.debug("props {}", props);
                        
                        double x = Double.parseDouble(props.getProperty("Absolute upper-left X"));
                        double y = Double.parseDouble(props.getProperty("Absolute upper-left Y"));
                        double width = Double.parseDouble(props.getProperty("Width"));
                        double height = Double.parseDouble(props.getProperty("Height"));
                        Rectangle2D result = new Rectangle2D.Double(x, y, width, height);
                        return result;
                    }
                    log.debug("not interested in {}", window);
                }
                // don't know which window to choose
                return getDesktopSize();
            } else {
                throw new UnsupportedOperationException("not implemented");
            }
        } catch (Exception e) {
            log.error("Could not find out capture size", e);
            return getDesktopSize();
        }
    }

    /**
     * Returns the directory hosting log and replay for the latest game run.
     * For every game JSettlers creates a new directory containing logfile and 
     * replayfile.
     * 
     * @return the file
     */
    public static File getLatestLogDir() {
        log.debug("getLatestLogDir()");
        
        File logs = new File(getVarFolder(), "logs");
        File[] logdirs = logs.listFiles();
        File latest = null;
        
        for (File f: logdirs) {
            if (latest == null) {
                latest = f;
            } else {
                if (latest.lastModified() < f.lastModified()) {
                    latest = f;
                }
            }
        }
        
        log.debug("latest log in {}", latest);
        return latest;
    }
    
    private static boolean copyGameDataIfExists(File src, File dst, String subfolder) throws IOException {
        log.debug("copyGameDataIfExists({}, {}, {})", src, dst, subfolder);
        
        File s2 = new File(src, subfolder);
        File d2 = new File(dst, subfolder);
        
        if (!d2.isDirectory()) {
            d2.mkdirs();
        }
        
        if (s2.isDirectory()) {
            FileUtils.copyDirectory(s2, d2);        
        }
        return d2.isDirectory();
    }
    
    /**
     * Copies only the required game data from an S3 folder.
     * Required are GFX, MAP and SND files.
     * 
     * @param src The S3 folder
     * @param dst The destination folder for copying to
     * @throws IOException something went wrong
     */
    public static void copyGameData(File src, File dst) throws IOException {
        copyGameDataIfExists(src, dst, "gfx");
        copyGameDataIfExists(src, dst, "GFX");
        copyGameDataIfExists(src, dst, "map");
        copyGameDataIfExists(src, dst, "MAP");
        copyGameDataIfExists(src, dst, "snd");
        copyGameDataIfExists(src, dst, "SND");
    }
    
//    public static void removeAllButFive() {
//        throw new UnsupportedOperationException("not yet implemented");
////        // remove if we have more than five
////        while (installedReleases.size()>5) {
////            Release r = installedReleases.get(installedReleases.size()-1);
////            Util.removeRelease(r);
////            installedReleases = Util.getInstalledReleases();
////        }
//    }

    /**
     * Removes temporary files older than 7 days.
     */
    public static void cleanTemp() {
        File temp = getManagedTempFolder();
        if (temp.isDirectory()) {
            Date threshold = Date.from(Instant.now().minus(7, ChronoUnit.DAYS));
            for (File f: temp.listFiles()) {
                if (new Date(f.lastModified()).before(threshold)) {
                    log.info("deleting old temp file {}", f);
                    if (f.isDirectory()) {
                        deleteDir(f);
                    } else {
                        f.delete();
                    }
                }
            }
        }
    }
    
    /**
     * Returns the folder containing the Settlers music files.
     * 
     * @return the folder reference
     */
    public static File getMusicFolder() {
        //return new File(getDataFolder(), "MUSIC");
        return new File(getDataFolder(), "THEME");
    }

    /**
     * Downloads a file - unless the target exists already.
     * 
     * @param url the url to downlad from
     * @param target the file to save it to
     */
    private static void downloadIfNotExists(URL url, File target) {
        log.info("downloadIfNotExists({}, {})", url, target);
        try {
            if (!target.exists()) {
                target.getParentFile().mkdirs();
                download(url, new File(target.getParentFile(), new File(url.getFile()).getName()));
            } else {
                log.info("skipped");
            }
        } catch (IOException e) {
            log.warn("Could not download {}", url, e);
        }
    }
    
    /**
     * Downloads music soundtrack from internet.
     * 
     * @throws MalformedURLException 
     */
    public static void downloadMusic() throws MalformedURLException, IOException, InterruptedException {
        log.warn("downloadMusic()");
        
        log.info("Supported audio formats:");
        for (AudioFileFormat.Type type: AudioSystem.getAudioFileTypes()) {
            log.info("    {}", type);
        }
        
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/cjllbhbk/Track02.mp3"), 
                new File(getMusicFolder(), "Track02.ogg"));
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/yqtjxvfd/Track03.mp3"), 
                new File(getMusicFolder(), "Track03.ogg"));
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/guohbfll/Track04.mp3"), 
                new File(getMusicFolder(), "Track04.ogg"));
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/qshzkbmf/Track05.mp3"), 
                new File(getMusicFolder(), "Track05.ogg"));
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/ditkixfr/Track06.mp3"), 
                new File(getMusicFolder(), "Track06.ogg"));
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/xncrscbv/Track07.mp3"), 
                new File(getMusicFolder(), "Track07.ogg"));
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/xbicjisa/Track08.mp3"), 
                new File(getMusicFolder(), "Track08.ogg"));
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/qiapnlgt/Track09.mp3"), 
                new File(getMusicFolder(), "Track09.ogg"));
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/sxensioh/Track10.mp3"), 
                new File(getMusicFolder(), "Track10.ogg"));
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/oiclfhiy/Track11.mp3"), 
                new File(getMusicFolder(), "Track11.ogg"));
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/mjaqypyv/Track12.mp3"), 
                new File(getMusicFolder(), "Track12.ogg"));
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/xcuqzsvg/Track13.mp3"), 
                new File(getMusicFolder(), "Track13.ogg"));
        downloadIfNotExists(new URL("https://vgmsite.com/soundtracks/settlers-iii-ultimate-collection-music/yypsitan/Track14.mp3"), 
                new File(getMusicFolder(), "Track14.ogg"));
        
        // convert mp3 to ogg
        ProcessBuilder pb = new ProcessBuilder("dir2ogg", "--directory", getMusicFolder().getAbsolutePath());
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();
        p.waitFor();
        int rc = p.exitValue();
        log.info("returned with {}", rc);
        
        // remove mp3
        File[] tracks = getMusicFolder().listFiles();
        for (File track: tracks) {
            if (track.getName().endsWith(".mp3")) {
                track.delete();
            }
        }
    }
}
