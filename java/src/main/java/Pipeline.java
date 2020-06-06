import dependencies.Config;
import dependencies.Emailer;
import dependencies.Logger;
import dependencies.Project;

public class Pipeline {
    private final Config config;
    private final Emailer emailer;
    private final Logger log;

    public Pipeline(Config config, Emailer emailer, Logger log) {
        this.config = config;
        this.emailer = emailer;
        this.log = log;
    }

    public void run(Project project) {
        boolean testsPassed = true;
        boolean deploySuccessful;
        boolean canDeploy = true;

        if (project.hasTests()) {
            testsPassed = runTests(project);
            canDeploy = testsPassed;
        }

        if (project.hasTests()) {
            if (testsPassed) {
                log.info("Tests passed");
            } else {
                log.error("Tests failed");
            }
        } else {
            log.info("No tests");
        }

        if (canDeploy) {
            deploySuccessful = deployProject(project);
        } else {
            deploySuccessful = false;
        }

        if (canDeploy) {
            if (deploySuccessful) {
                log.info("Deployment successful");
            } else {
                log.error("Deployment failed");
            }
        }

        if (config.sendEmailSummary()) {
            log.info("Sending email");
            String message;
            if (testsPassed) {
                if (deploySuccessful) {
                    message = "Deployment completed successfully";
                } else {
                    message = "Deployment failed";
                }
            } else {
                message = "Tests failed";
            }
            emailer.send(message);
        } else {
            log.info("Email disabled");
        }
    }

    private boolean deployProject(final Project project) {
        return "success".equals(project.deploy());
    }

    private boolean runTests(final Project project) {
        return "success".equals(project.runTests());
    }
}
