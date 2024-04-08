/*
 */
package settlers.installer;

import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import settlers.installer.ui.ConfigurationPanel;
import settlers.installer.ui.InstallSourcePicker;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JWindow;
import javax.swing.event.HyperlinkEvent;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHAuthorization;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHObject;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAbuseLimitHandler;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitHubRateLimitHandler;
import org.kohsuke.github.HttpException;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.RateLimitChecker;
import org.kohsuke.github.connector.GitHubConnectorResponse;
import settlers.installer.model.Configuration;
import settlers.installer.model.GameVersion;
import settlers.installer.ui.BugReport;
import settlers.installer.ui.GameList;
import settlers.installer.ui.LoadingIndicator2;

/**
 *
 * @author hiran
 */
public class App extends javax.swing.JFrame {
    private static final Logger log = LogManager.getLogger(App.class);

    private final javax.swing.ImageIcon iiFound = new javax.swing.ImageIcon(getClass().getResource("/images/done_outline_FILL0_wght400_GRAD0_opsz48.png"));
    private final javax.swing.ImageIcon iiMissing = new javax.swing.ImageIcon(getClass().getResource("/images/dangerous_FILL0_wght400_GRAD0_opsz48.png"));
    private final javax.swing.ImageIcon iiUpdate = new javax.swing.ImageIcon(getClass().getResource("/images/update_FILL0_wght400_GRAD0_opsz48.png"));
    
    private Configuration configuration;
    private GitHub github;
    private GameList gameList;
    private JWindow bugButton;
    
    // TODO: Play button should come like https://www.codejava.net/java-se/swing/how-to-create-drop-down-button-in-swing
    
    /**
     * Creates new form App.
     */
    public App() {
        initComponents();
        jProgressBar.setVisible(false);
        
        configuration = Configuration.load(Util.getConfigurationFile());
        try {
            GitHubBuilder githubBuilder = new GitHubBuilder();
            githubBuilder.withAbuseLimitHandler(new GitHubAbuseLimitHandler() {
                @Override
                public void onError(GitHubConnectorResponse ghcr) throws IOException {
                    log.error("GitHubAbuseLimitHandler onError(...)");
                    JOptionPane.showMessageDialog(null, "Abuse limit applies...");
                }
            });
            githubBuilder.withRateLimitHandler(new GitHubRateLimitHandler() {
                @Override
                public void onError(GitHubConnectorResponse ghcr) throws IOException {
                    log.error("GitHubRateLimitHandler onError(...)");
                    JOptionPane.showMessageDialog(null, "Rate limit applies...");
                }
            });
            githubBuilder.withRateLimitChecker(new RateLimitChecker() {
                @Override
                protected boolean checkRateLimit(GHRateLimit.Record rateLimitRecord, long count) throws InterruptedException {
                    log.debug("checkRateLimit({}, {})", rateLimitRecord, count);
                    
                    // if we are too low, bail out
                    if (rateLimitRecord.getRemaining()<10) {
                        log.warn("Too low rate limit. Bailing out");
                        return true;
                    }
                    
                    // if we are getting low, delay the requests a bit
                    if (rateLimitRecord.getRemaining()<100) {
                        log.warn("Low rate limit. Throttling speed");
                        Thread.sleep(1000- 10*rateLimitRecord.getRemaining());
                    }
                    
                    return false;
                }                
            });
            if (configuration.getGithubUsername() != null) {
                log.debug("GitHub with Authentication");
                //githubBuilder.withOAuthToken(configuration.getGithubToken(), configuration.getGithubUsername());
                githubBuilder.withPassword(configuration.getGithubUsername(), configuration.getGithubToken());
            } else {
                log.debug("GitHub anonymously");
            }
            github = githubBuilder.build();
            
            log.info("GitHub Credentials valid: {}", github.isCredentialValid());
            
            GHRateLimit ghrl = github.getRateLimit();
            log.info("GitHub Rate Limit Core:                 {}", ghrl.getCore());
            log.info("GitHub Rate Limit GraphQL:              {}", ghrl.getGraphQL());
            log.info("GitHub Rate Limit Integration Manifest: {}", ghrl.getIntegrationManifest());
            log.info("GitHub Rate Limit Search:               {}", ghrl.getSearch());

            if (!github.isAnonymous()) {
                // this requires authenticated connections
                GHMyself myself = github.getMyself();
                log.debug("myself: {}", myself);
                List<GHAuthorization> auths = github.listMyAuthorizations().toList();
                for (GHAuthorization auth: auths) {
                    log.debug("  {}", auth);
                }
                
            }
//            List<GHAuthorization> auths = github.listMyAuthorizations().toList();
//            for (GHAuthorization auth: auths) {
//                log.debug("  {}", auth);
//            }
        } catch (HttpException e) {
            log.debug("HttpExceptionException? {}", e.getResponseMessage(), e);
            JOptionPane.showMessageDialog(null, "Problems with GitHub connection. Running with reduced functionality.");
        } catch (GHFileNotFoundException e) {
            log.debug("GHFileNotFound? {}", e.getResponseHeaderFields(), e);
        } catch (Exception e) {
            log.error("Could not initialize github client", e);
            JOptionPane.showMessageDialog(null, "Serious error. Check logfiles.");
            System.exit(1);
        }
        
        gameList = new GameList();
        add(gameList, new GridBagConstraints(3, 1, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        checkFiles();
        pack();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        lbIconGithub = new javax.swing.JLabel();
        lbGameFiles = new javax.swing.JLabel();
        lbIconSettlers = new javax.swing.JLabel();
        lbDataFiles = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        lbResultData = new javax.swing.JLabel();
        btInstallData = new javax.swing.JButton();
        buttonBar = new javax.swing.JPanel();
        jProgressBar = new javax.swing.JProgressBar();
        btPlay = new javax.swing.JButton();
        btTools = new javax.swing.JButton();
        btOptions = new javax.swing.JButton();
        btMapCreator = new javax.swing.JButton();
        btForum = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Settlers-Installer");
        setName("Settlers-Installer"); // NOI18N
        getContentPane().setLayout(new java.awt.GridBagLayout());

        lbIconGithub.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbIconGithub.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/GitHub-Mark-120px-plus.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.ipadx = 30;
        gridBagConstraints.ipady = 30;
        getContentPane().add(lbIconGithub, gridBagConstraints);

        lbGameFiles.setFont(new java.awt.Font("Ubuntu", 1, 15)); // NOI18N
        lbGameFiles.setText("Game files");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.ipadx = 30;
        gridBagConstraints.ipady = 30;
        getContentPane().add(lbGameFiles, gridBagConstraints);

        lbIconSettlers.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbIconSettlers.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/siedler3-helme-circle-120.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.ipadx = 30;
        gridBagConstraints.ipady = 30;
        getContentPane().add(lbIconSettlers, gridBagConstraints);

        lbDataFiles.setFont(new java.awt.Font("Ubuntu", 1, 15)); // NOI18N
        lbDataFiles.setText("Data Files");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.ipadx = 30;
        gridBagConstraints.ipady = 30;
        getContentPane().add(lbDataFiles, gridBagConstraints);

        jLabel5.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel5.setText("Welcome to settlers-remake, a clone of Bluebyte’s Settlers III.");
        jLabel5.setAutoscrolls(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.ipadx = 30;
        gridBagConstraints.ipady = 30;
        gridBagConstraints.weightx = 1.0;
        getContentPane().add(jLabel5, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        getContentPane().add(lbResultData, gridBagConstraints);

        btInstallData.setBackground(java.awt.Color.orange);
        btInstallData.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/download_for_offline_FILL0_wght400_GRAD0_opsz48.png"))); // NOI18N
        btInstallData.setText("Install Data");
        btInstallData.setBorderPainted(false);
        btInstallData.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btInstallDataActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        getContentPane().add(btInstallData, gridBagConstraints);

        buttonBar.setLayout(new java.awt.GridBagLayout());

        jProgressBar.setIndeterminate(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        buttonBar.add(jProgressBar, gridBagConstraints);

        btPlay.setBackground(new java.awt.Color(127, 255, 131));
        btPlay.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/play_arrow_FILL0_wght400_GRAD0_opsz48.png"))); // NOI18N
        btPlay.setToolTipText("Play game!");
        btPlay.setOpaque(true);
        btPlay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btPlayActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        buttonBar.add(btPlay, gridBagConstraints);

        btTools.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/home_repair_service_FILL0_wght400_GRAD0_opsz48.png"))); // NOI18N
        btTools.setToolTipText("Toolbox");
        btTools.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btToolsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        buttonBar.add(btTools, gridBagConstraints);

        btOptions.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/menu_FILL0_wght400_GRAD0_opsz48.png"))); // NOI18N
        btOptions.setToolTipText("Options");
        btOptions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btOptionsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 1;
        buttonBar.add(btOptions, gridBagConstraints);

        btMapCreator.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/public_FILL0_wght400_GRAD0_opsz48.png"))); // NOI18N
        btMapCreator.setToolTipText("Map Creator");
        btMapCreator.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btMapCreatorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        buttonBar.add(btMapCreator, gridBagConstraints);

        btForum.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/forum_FILL0_wght400_GRAD0_opsz48.png"))); // NOI18N
        btForum.setToolTipText("Forum");
        btForum.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btForumActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 1;
        buttonBar.add(btForum, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipadx = 30;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        getContentPane().add(buttonBar, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void doInstallGame() {
        log.debug("doInstallGame()");
        btInstallData.setEnabled(false);
        btPlay.setEnabled(false);
        btOptions.setEnabled(false);
        jProgressBar.setVisible(true);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                int x = 0;
                try {

                    Util.installLatest(github);
                    
                } catch(Exception e) {
                    log.debug("Could not install game", e);
                    JOptionPane.showMessageDialog(App.this, "Something went wrong.");
                } finally {
                    btInstallData.setEnabled(true);
                    btPlay.setEnabled(true);
                    btOptions.setEnabled(true);
                    jProgressBar.setVisible(false);
                    
                    checkFiles();
                }
            }
        }).start();
    }
    
    private void btInstallDataActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btInstallDataActionPerformed
        log.debug("btInstallDataActionPerformed(...)");
        btInstallData.setEnabled(false);
        btPlay.setEnabled(false);
        
        // check parameters
        InstallSourcePicker isp = new InstallSourcePicker();
        if (JOptionPane.showOptionDialog(this, isp, "Install Data files from...", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null) == JOptionPane.OK_OPTION) {
            // do the needful
            String source = isp.getPath();
            log.debug("Will grab files from {}", source);
            File srcDir = new File(source);

            jProgressBar.setVisible(true);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    log.debug("Started installing from {} to {}", srcDir, Util.getDataFolder());
                    try {
                        if (Util.isGameFolder(srcDir)) {
                            log.debug("Want to copy files...");
                            Util.copyGameData(srcDir, Util.getDataFolder());
                            
                        } else if (Util.isInstallCD(srcDir)) {
                            log.debug("Want to install from CD");
                            Util.installFromCD(srcDir);
                        } else throw new Exception(String.format("Unknown source %s", srcDir));

                    } catch(Exception e) {
                        log.error("Could not install data from {}", srcDir, e);
                        JOptionPane.showMessageDialog(App.this, "Something went wrong.");
                    } finally {
                        btInstallData.setEnabled(true);
                        btPlay.setEnabled(true);
                        jProgressBar.setVisible(false);

                        checkFiles();
                    }
                }
            }).start();
        } else {
            btInstallData.setEnabled(true);
            btPlay.setEnabled(true);
            jProgressBar.setVisible(false);
            checkFiles();
        }

    }//GEN-LAST:event_btInstallDataActionPerformed

    private void showBugButton() {
        log.debug("showBugButton()");
        
        // check if we are logged in
        try {
            github.getRateLimit().getRemaining();
        } catch (Exception e) {
            log.warn("seems we are not logged in. Do not show the bug button as we cannot raise issues anyway.", e);
            return;
        }
        
        if (bugButton == null) {
            bugButton = new JWindow();
            javax.swing.ImageIcon iiBRR = new javax.swing.ImageIcon(getClass().getResource("/images/bug_report_RED.png"));            
            JButton button = new JButton(iiBRR);
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    try {
                        File logdir = Util.getLatestLogDir();
                        File logfile = new File(logdir, logdir.getName()+"_out.log");
                        File replayfile = new File(logdir, logdir.getName()+"_replay.log");

                        Robot robot = new Robot();
                        BufferedImage i = robot.createScreenCapture(Util.getCaptureSize().getBounds());
                        BugReport br = new BugReport();
                        br.setImage(i);
                        br.setLogfile(logfile.getAbsolutePath());
                        br.setReplayFile(replayfile.getAbsolutePath());
                        if (JOptionPane.showOptionDialog(bugButton, br, "Report issue to " + Util.GITHUB_REPO_NAME_ISSUES + "...", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null)==JOptionPane.OK_OPTION) {
                            log.info("Need to create issue...");
                            GHRepository repository = github.getRepository(Util.GITHUB_REPO_NAME_ISSUES);

                            StringBuilder issueBody = new StringBuilder(br.getDescription()).append("\n");
                            
                            // see important properties
                            // https://docs.oracle.com/en/java/javase/17/docs/api/system-properties.html
                            issueBody.append("\nHere is some data about my system:");
                            issueBody.append("\n");
                            issueBody.append("\nOS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version"));
                            issueBody.append("\nJVM: ").append(System.getProperty("java.vm.name"))
                                    .append(" ").append(System.getProperty("java.runtime.version"));
                            {
                                Object current = gameList.getSelection();
                                if (current instanceof GHRelease) {
                                    issueBody.append("\nSettlers Remake: Release ").append(((GHRelease)current).getName()).append("\n");
                                } else if (current instanceof GHWorkflowRun) {
                                    GHWorkflowRun run = (GHWorkflowRun)current;
                                    issueBody
                                            .append("\nSettlers Remake: [Workflow Run ")
                                            .append(run.getHeadBranch())
                                            .append(" ")
                                            .append(String.valueOf(run.getRunNumber()))
                                            .append("](")
                                            .append(run.getHtmlUrl())
                                            .append(")\n");
                                } else if (current instanceof GameVersion) {
                                    issueBody.append("\nSettlers Remake: ").append(((GameVersion)current).getBasedOn()).append("\n");
                                }
                            }
                            issueBody.append("\n");
                            
                            if (br.isAttachScreenshot()) {
                                log.debug("uploading screenshot...");
                                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                ImageIO.write(i, "png", bos);

//                                GHBlob blob = repository.createBlob().binaryContent(bos.toByteArray()).create();
//                                issueBody.append("\n![Screenshot](").append(blob.getUrl()).append(")");
//
//                                GHGist gist = github.createGist()
//                                        .file("screenshot", IOUtils.toString(bos.toByteArray(), "UTF-8"))
//                                        .create();
                                
//                                BucketResponse bresp = UfileClient.bucket(null)
//                                        .createBucket("jsettlers-screenshot", "EU", BucketType.PUBLIC)
//                                        .execute();
//                                
//                                UfileClient.bucket(null).;
                                
//                                issueBody.append("\n![Screenshot](").append(gist.getUrl()).append(")");
                            }
                            if (br.isAttachLogfile()) {
                                log.debug("uploading logfile...");
                                String logdata = FileUtils.readFileToString(logfile, "UTF-8");
                                if (logdata.length()>65000) {
                                    logdata = logdata.substring(logdata.length()-65000);
                                }
                                
// we cannot create an external resource, therefore we will add the data into the issue directly
//                                GHBlob blob = repository.createBlob().textContent(logdata).create();
//                                issueBody.append("\nLogfile at ").append(blob.getUrl());

                                issueBody.append("Here is the last 65k of my latest logfile (").append(logfile.getName()).append("):");
                                issueBody.append("\n```\n").append(logdata).append("\n```\n");
                            }
//                            if (br.isAttachReplayfile()) {
//                                log.debug("uploading replay file...");
//                                GHBlob blob = repository.createBlob().textContent(FileUtils.readFileToString(replayfile, "UTF-8")).create();
//                                issueBody.append("\nReplay File at ").append(blob.getUrl());
//                            }                            
                            
                            log.debug("Creating issue...");
                            GHIssue issue = repository.createIssue(br.getTitle()).body(issueBody.toString()).label("settlers-installer").create();
                            log.debug("Created issue {}/{}", repository.getFullName(), issue.getNumber());
                            
                            StringBuilder sb = new StringBuilder("<html>Created issue <a href=\"")
                                    .append(issue.getHtmlUrl()).append("\">").append(repository.getFullName()).append(" ")
                                    .append(issue.getNumber()).append("</a></html>");
                            
                            JEditorPane jep = new JEditorPane();
                            jep.setContentType("text/html");
                            jep.setText(sb.toString());
                            jep.setEditable(false);
                            jep.addHyperlinkListener(e -> {
                                if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType())) {
                                    try {
                                        Desktop.getDesktop().browse(e.getURL().toURI());
                                    } catch (Exception ex) {
                                        log.error("Could not activate browser", ex);
                                    }
                                }
                            });
                            jep.setOpaque(false);
                            
                            JOptionPane.showMessageDialog(bugButton, jep);

//                            //i.createGraphics();
//                            // draw with graphics if needed
//
//                            ImageIO.write(i, "png", new File("/home/hiran/screenshot.png"));
                        }                        
                    } catch (GHFileNotFoundException e) {
                        log.error("Could not report issue {}", e.getResponseHeaderFields(), e);
                        JOptionPane.showMessageDialog(bugButton, "Could not create issue.");
                    } catch (Exception e) {
                        log.error("Could not report issue", e);
                        JOptionPane.showMessageDialog(bugButton, "Could not create issue.");
                    }
                }
            });
            bugButton.add(button);
            bugButton.pack();
            bugButton.setAlwaysOnTop(true);
            
            bugButton.setLocationByPlatform(true);
        }
        bugButton.setVisible(true);
    }
    
    private void btPlayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btPlayActionPerformed
        log.debug("btPlayActionPerformed(...)");
        if (gameList.getSelection()==null) {
            JOptionPane.showMessageDialog(this, "Please select a game version first.");
            return;
        }
        
        btInstallData.setEnabled(false);
        btPlay.setEnabled(false);
        btOptions.setEnabled(false);
        jProgressBar.setVisible(true);
        // setVisible(false); let's stay visible until an eventually needed installation is done
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                int x = 0;
                try {
                    try {
                        Util.downloadMusic();
                    } catch (Exception e) {
                        log.warn("Problem downloading music", e);
                    }

                    
                    Object game = gameList.getSelection();
                    if (game instanceof GHObject) {
                        if (!Util.isInstalled((GHObject)game)) {
                            if (github.getRateLimit().getRemaining()>1) {
                            Util.installGame((GHObject)game);
                            } else {
                                throw new Exception("Cannot install. GitHub Rate limit exceeded");
                            }
                        }
                    }

                    if (configuration.isSupportBugReporting()) {
                        // show but button
                        showBugButton();
                    }
                    setVisible(false);

                    log.info("running {}", game);
                    if (game instanceof GameVersion) {
                        //Util.runGame((GameVersion)game);
                        Util.execGameJar((GameVersion)game, "JSettlers/JSettlers.jar");
                    } else if (game instanceof GHObject) {
                        Util.execGameJar((GHObject)game, "JSettlers/JSettlers.jar");
                    }
                    
                } catch(Exception e) {
                    log.error("could not run game", e);
                    JOptionPane.showMessageDialog(App.this, "Something went wrong:\n"+e.getMessage());
                } finally {
                    // hide bug button
                    if (bugButton != null) {
                        bugButton.setVisible(false);
                    }
                    
                    btInstallData.setEnabled(true);
                    btPlay.setEnabled(true);
                    btOptions.setEnabled(true);
                    jProgressBar.setVisible(false);
                    setVisible(true);

                    checkFiles();
                }
            }
        }).start();
    }//GEN-LAST:event_btPlayActionPerformed

    private void btOptionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btOptionsActionPerformed
        log.debug("btOptionsActionPerformed(...)");
        ConfigurationPanel cp = new ConfigurationPanel();
        cp.setData(configuration);
        if (JOptionPane.showOptionDialog(this, cp, "Preferences", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null)==JOptionPane.OK_OPTION) {
            configuration = cp.getData();
            configuration.save(Util.getConfigurationFile());
        }
    }//GEN-LAST:event_btOptionsActionPerformed

    private void btToolsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btToolsActionPerformed
        log.debug("btToolsActionPerformed(...)");
        if (gameList.getSelection()==null) {
            JOptionPane.showMessageDialog(this, "Please select a game version first.");
            return;
        }
        
        btInstallData.setEnabled(false);
        btPlay.setEnabled(false);
        btOptions.setEnabled(false);
        jProgressBar.setVisible(true);
        // setVisible(false); let's stay visible until an eventually needed installation is done
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                int x = 0;
                try {

                    Object game = gameList.getSelection();
                    if (game instanceof GHObject) {
                        if (!Util.isInstalled((GHObject)game)) {
                            if (github.getRateLimit().getRemaining()>1) {
                                Util.installGame((GHObject)game);
                            } else {
                                throw new Exception("Cannot install. GitHub Rate limit exceeded");
                            }
                        }
                    }

                    if (configuration.isSupportBugReporting()) {
                        // show but button
                        showBugButton();
                    }
                    setVisible(false);

                    log.info("running {}", game);
                    if (game instanceof GameVersion) {
                        Util.execGameJar((GameVersion)game, "JSettlers/JSettlersTools.jar");
                    } else if (game instanceof GHObject) {
                        Util.execGameJar((GHObject)game, "JSettlers/JSettlersTools.jar");
                    }
                    
                } catch(Exception e) {
                    log.error("could not run tools", e);
                    JOptionPane.showMessageDialog(App.this, "Something went wrong:\n"+e.getMessage());
                } finally {
                    // hide bug button
                    if (bugButton != null) {
                        bugButton.setVisible(false);
                    }
                    
                    btInstallData.setEnabled(true);
                    btPlay.setEnabled(true);
                    btOptions.setEnabled(true);
                    jProgressBar.setVisible(false);
                    setVisible(true);

                    checkFiles();
                }
            }
        }).start();
    }//GEN-LAST:event_btToolsActionPerformed

    private void btMapCreatorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btMapCreatorActionPerformed
        log.debug("btMapCreatorActionPerformed(...)");
        if (gameList.getSelection()==null) {
            JOptionPane.showMessageDialog(this, "Please select a game version first.");
            return;
        }
        
        btInstallData.setEnabled(false);
        btPlay.setEnabled(false);
        btOptions.setEnabled(false);
        jProgressBar.setVisible(true);
        // setVisible(false); let's stay visible until an eventually needed installation is done
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                int x = 0;
                try {

                    Object game = gameList.getSelection();
                    if (game instanceof GHObject) {
                        if (!Util.isInstalled((GHObject)game)) {
                            if (github.getRateLimit().getRemaining()>1) {
                                Util.installGame((GHObject)game);
                            } else {
                                throw new Exception("Cannot install. GitHub Rate limit exceeded");
                            }
                        }
                    }

                    if (configuration.isSupportBugReporting()) {
                        // show but button
                        showBugButton();
                    }
                    setVisible(false);

                    log.info("running {}", game);
                    if (game instanceof GameVersion) {
                        Util.execGameJar((GameVersion)game, "JSettlers/MapCreator.jar");
                    } else if (game instanceof GHObject) {
                        Util.execGameJar((GHObject)game, "JSettlers/MapCreator.jar");
                    }
                    
                } catch(Exception e) {
                    log.error("could not run tools", e);
                    JOptionPane.showMessageDialog(App.this, "Something went wrong:\n"+e.getMessage());
                } finally {
                    // hide bug button
                    if (bugButton != null) {
                        bugButton.setVisible(false);
                    }
                    
                    btInstallData.setEnabled(true);
                    btPlay.setEnabled(true);
                    btOptions.setEnabled(true);
                    jProgressBar.setVisible(false);
                    setVisible(true);

                    checkFiles();
                }
            }
        }).start();

    }//GEN-LAST:event_btMapCreatorActionPerformed

    private void btForumActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btForumActionPerformed
        URI uri = null;
        try {
            uri = new URI("https://discord.gg/2hVV4u6");
            Desktop desktop = Desktop.getDesktop();
            desktop.browse(uri);
        } catch (Exception e) {
            log.error("Could not open forum", e);
            JOptionPane.showMessageDialog(this, "Error. Please go to "+uri+" manually.");
        }
        
    }//GEN-LAST:event_btForumActionPerformed

    private void checkFiles() {
        log.debug("checkFiles()");

//        LoadingIndicator li = new LoadingIndicator();
//        li.setText("Scanning GitHub...");
        LoadingIndicator2 li = new LoadingIndicator2();
        setGlassPane(li);
        li.setVisible(true);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    haveGameFiles();

                    boolean dataFiles = haveDataFiles();
                    lbResultData.setIcon(dataFiles? iiFound: iiMissing);
                    lbResultData.setVisible(dataFiles);
                    btInstallData.setVisible(!dataFiles);

                    btPlay.setVisible(dataFiles);

                    if (gameList.getData().isEmpty()) {
                        try {
                            GHRateLimit.Record limit = github.getRateLimit().getCore();
                            String msg = String.format("We have no games to show, and the GitHub Rate Limit is %d/%d until %s", limit.getRemaining(), limit.getLimit(), limit.getResetDate());
                            log.debug(msg);
                            JOptionPane.showMessageDialog(App.this, msg);
                        } catch (Exception e) {
                            log.error("could not show github rate limit", e);
                            JOptionPane.showMessageDialog(App.this, "We have no games and cannot even tell the GitHug Rate Limit.");
                        }
                    }
                } finally {
                    App.this.getGlassPane().setVisible(false);
                }
            }
        }).start();
    }
    
    public enum GameState {
        missing, old, latest
    }
    
    private void printCounts(GHRepository repository) {
        log.debug("printCounts(...)");
        try {
            
            PagedIterable<GHRelease> releases = repository.listReleases();
            int count = 0;
            for(PagedIterator<GHRelease> iter = releases.iterator(); iter.hasNext(); ) {
                GHRelease i = iter.next();
                count++;
            }
            log.debug("counted {} releases", count);
            
            PagedIterable<GHArtifact> artifacts = repository.listArtifacts();
            count = 0;
            for (PagedIterator<GHArtifact> iter = artifacts.iterator(); iter.hasNext(); ) {
                GHArtifact i = iter.next();
                count++;
                //log.debug("Artifact {}", i);
            }
            log.debug("counted {} artifacts", count);
            
            PagedIterable<GHWorkflow> workflows = repository.listWorkflows();
            count = 0;
            for (PagedIterator<GHWorkflow> iter = workflows.iterator(); iter.hasNext(); ) {
                GHWorkflow i = iter.next();
                count++;
                //log.debug("Workflow {}", i);
                
                int count2 = 0;
                PagedIterable<GHWorkflowRun> workflowRuns = i.listRuns();
                for (PagedIterator<GHWorkflowRun> iter2 = workflowRuns.iterator(); iter2.hasNext(); ) {
                    GHWorkflowRun j = iter2.next();
                    //log.debug("  run: {}", j);
                    count2++;
                }
                log.debug("counted {} runs", count2);
            }
            log.debug("counted {} workflows", count);
            
        } catch (IOException e) {
            log.error("Could not list releases", e);
        }
    }
    
    private void buildLocalList(List<Object> availableGames) {
        log.debug("we are desperate. Show installed games...");
        for (GameVersion gv: Util.getInstalledGames()) {
            availableGames.add(gv);
        }
    }
    
    /**
     * Checks the available games and updates the game list.
     */
    private void haveGameFiles() {
        log.debug("haveGameFiles()");

        List<Object> availableGames = new ArrayList<>();
        if (github != null) {
            log.debug("github anonymous: {}", github.isAnonymous());
            log.debug("github offline:   {}", github.isOffline());

            GHRepository repository = null;
            try {
                availableGames.addAll(Util.getAvailableGames(github, !configuration.isCheckArtifacts()));
            } catch (IOException e) {
                log.error("Could not check online games", e);
            }
            gameList.setData(availableGames);

            List<GameVersion> installedGames = Util.getInstalledGames();
            if (availableGames.isEmpty()) {
                buildLocalList(availableGames);
                gameList.setData(availableGames);
            } else {

                if (installedGames != null && !installedGames.isEmpty()) {
                    // check if updates are available

                    try {
                        Date installed = installedGames.get(0).getInstalledAt();
                        if (installed == null) {
                        }

                        Object first = availableGames.get(0);
                        if (first instanceof GHObject) {
                            Date available = ((GHObject)first).getUpdatedAt();
                            if (available == null) {
                                available = ((GHObject)first).getCreatedAt();
                            }
                            if (installed.before(available)) {
                                // update is available
                                log.debug("Update is available");
                            } else {
                                // we already have the latest version
                                log.debug("we already have the latest version");
                            }
                        } else {
                        }
                    } catch (Exception e) {
                        // could not figure out if update is available. Let's assume we have the latest
                        log.debug("We assume to have the latest version", e);
                    }
                } else {
                    log.debug("No good version installed locally");
                }
            }
        } else {
            availableGames = new ArrayList<>();
            buildLocalList(availableGames);
            gameList.setData(availableGames);
        }
    }

    /**
     * Validated a S3 data folder.
     * 
     * @return true if data files seem ok, false otherwise
     */
    private boolean haveDataFiles() {
        log.debug("haveDataFiles()");
        
        File dir = Util.getDataFolder();
        
        if (!dir.isDirectory()) {
            log.warn("File {} is not a directory.", dir);
            return false;
        }
        if (dir.listFiles().length < 2) {
            log.warn("File {} contains too few files", dir);
            return false;
        }
        return true;
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        log.debug("main({})", Arrays.asList(args));
        log.debug("Full command line: {}", ProcessHandle.current().info().commandLine().orElse("n/a"));
        Util.dumpEnvironment();
        Util.dumpProperties(System.getProperties());
        Util.cleanTemp();
        
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(App.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(App.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(App.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(App.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                App app = new App();
                app.setLocationRelativeTo(null);
                app.setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btForum;
    private javax.swing.JButton btInstallData;
    private javax.swing.JButton btMapCreator;
    private javax.swing.JButton btOptions;
    private javax.swing.JButton btPlay;
    private javax.swing.JButton btTools;
    private javax.swing.JPanel buttonBar;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JProgressBar jProgressBar;
    private javax.swing.JLabel lbDataFiles;
    private javax.swing.JLabel lbGameFiles;
    private javax.swing.JLabel lbIconGithub;
    private javax.swing.JLabel lbIconSettlers;
    private javax.swing.JLabel lbResultData;
    // End of variables declaration//GEN-END:variables
}
