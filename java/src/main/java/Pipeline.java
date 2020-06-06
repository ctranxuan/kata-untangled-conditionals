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
        boolean testsPassed;
        boolean deploySuccessful;

        if (project.hasTests()) {
            if ("success".equals(project.runTests())) {
                testsPassed = true;
            } else {
                testsPassed = false;
            }
        } else {
            testsPassed = true;
        }

        if (project.hasTests()) {
            if ("success".equals(project.runTests())) {
                log.info("Tests passed");
            } else {
                log.error("Tests failed");
            }
        } else {
            log.info("No tests");
        }

        if (testsPassed) {
            if ("success".equals(project.deploy())) {
                deploySuccessful = true;
            } else {
                deploySuccessful = false;
            }
        } else {
            deploySuccessful = false;
        }

        if (testsPassed) {
            if ("success".equals(project.deploy())) {
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
}
