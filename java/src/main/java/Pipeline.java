import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import dependencies.Config;
import dependencies.Emailer;
import dependencies.Logger;
import dependencies.Project;

public class Pipeline {
    private static final class Step {
        private final List<Runnable> onSuccess = new ArrayList<>();
        private final List<Runnable> onFail = new ArrayList<>();
        private final List<Runnable> onSkip = new ArrayList<>();
        private final Function<Project, Boolean> execute;
        private final Project project;

        Step(final Function<Project, Boolean> execute, Project project) {
            this.execute = execute;
            this.project = project;
        }

        Step onSuccess(final Runnable runnable) {
            onSuccess.add(runnable);
            return this;
        }

        Step onFail(final Runnable runnable) {
            onFail.add(runnable);
            return this;
        }

        Step onSkip(final Runnable runnable) {
            onSkip.add(runnable);
            return this;
        }

        boolean nextStep() {
            final Boolean success = execute.apply(project);
            if (success == null) { // FIXME clean code
                onSkip.forEach(Runnable::run);
                return true;
            }
            if (success) {
                onSuccess.forEach(Runnable::run);
            } else {
                onFail.forEach(Runnable::run);
            }
            return success;
        }
    }

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

        final Step runTests = new Step(this::runTests, project)
                                .onSuccess(() -> log.info("Tests passed"))
                                .onFail(() -> log.error("Tests failed"))
                                .onFail(() -> sendEmail(config, "Tests failed"))
                                .onSkip(() -> log.info("No tests"));

        final Step deployProject = new Step(this::deploy, project)
                                    .onSuccess(() -> log.info("Deployment successful"))
                                    .onSuccess(() -> sendEmail(config, "Deployment completed successfully"))
                                    .onFail(() -> log.error("Deployment failed"))
                                    .onFail(() -> sendEmail(config, "Deployment failed"));

        Stream.of(runTests, deployProject)
              .filter(Step::nextStep)
              .count();

        if (!config.sendEmailSummary()) {
            log.info("Email disabled");
        }

//        if (project.hasTests()) {
//            if ("success".equals(project.runTests())) {
//                log.info("Tests passed");
//                testsPassed = true;
//            } else {
//                log.error("Tests failed");
//                testsPassed = false;
//            }
//        } else {
//            log.info("No tests");
//            testsPassed = true;
//        }
//
//        if (testsPassed) {
//            if ("success".equals(project.deploy())) {
//                log.info("Deployment successful");
//                deploySuccessful = true;
//            } else {
//                log.error("Deployment failed");
//                deploySuccessful = false;
//            }
//        } else {
//            deploySuccessful = false;
//        }
//
//        if (config.sendEmailSummary()) {
//            log.info("Sending email");
//            if (testsPassed) {
//                if (deploySuccessful) {
//                    emailer.send("Deployment completed successfully");
//                } else {
//                    emailer.send("Deployment failed");
//                }
//            } else {
//                emailer.send("Tests failed");
//            }
//        } else {
//            log.info("Email disabled");
//        }
    }

    private Boolean deploy(final Project project) {
        return "success".equals(project.deploy());
    }

    private void sendEmail(final Config config, final String message) {
        if (config.sendEmailSummary()) {
            log.info("Sending email");
            emailer.send(message);
        }
    }

    private Boolean runTests(final Project project) {
        if (project.hasTests()) {
            return "success".equals(project.runTests());
        }
        return null;
    }
}
