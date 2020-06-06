import dependencies.Config;
import dependencies.Emailer;
import dependencies.Logger;
import dependencies.Project;
import io.vavr.control.Option;

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
        boolean deploySuccessful = false;

         new TestRunner()
                .onSuccess(() -> log.info("Tests passed"))
                .onError(() -> log.error("Tests failed"))
                .onNone(() -> log.info("No tests"))
                .run(project)
                .filter(testResult -> testResult.testsPassed)
                .flatMap(testResult -> new DeployRunner()
                                            .onSuccess(() -> log.info("Deployment successful"))
                                            .onError(() -> log.error("Deployment failed"))
                                            .deploy(testResult))
                .filter(__ -> config.sendEmailSummary())
                .onEmpty(() -> log.info("Email disabled"))
                .filter(deployResult -> deployResult.testsPassed)
                .map(deployResult -> deployResult.deploySuccessful ? "Deployment completed successfully"
                                                                   : "Deployment failed")

                .orElse(Option.of("Test failed"))
                .fold(() ->  new Runnable() {
                          @Override
                          public void run() {
                              log.info("Email disabled");
                          }
                      },
                      message -> new Runnable() {
                          @Override
                          public void run() {
                              sendEmail(message);
                          }
                      });
         ;

//        if (testResult.testsPassed) {
//            deploySuccessful =
//            new DeployRunner()
//                    .onSuccess(() -> log.info("Deployment successful"))
//                    .onError(() -> log.error("Deployment failed"))
//                    .deploy(testResult).get().deploySuccessful;
//        }
//
//        if (config.sendEmailSummary()) {
//            log.info("Sending email");
//            String message;
//            if (testResult.testsPassed) {
//                if (deploySuccessful) {
//                    message = "Deployment completed successfully";
//                } else {
//                    message = "Deployment failed";
//                }
//            } else {
//                message = "Tests failed";
//            }
//            emailer.send(message);
//        } else {
//            log.info("Email disabled");
//        }
    }

    private void sendEmail(final String message) {
        log.info("Sending email");
        emailer.send(message);
    }

    private boolean deployProject(final Project project) {
        return "success".equals(project.deploy());
    }

    private boolean runTests(final Project project) {
        return "success".equals(project.runTests());
    }

    static final class TestResult {
        private final Project project;
        private final boolean testsPassed;

        TestResult(final Project project, final boolean testsPassed) {
            this.project = project;
            this.testsPassed = testsPassed;
        }
    }

    private static class DeployResult {
        private final boolean deploySuccessful;
        private final boolean testsPassed;

        public DeployResult(final boolean deploySuccessful, final boolean testsPassed) {
            this.deploySuccessful = deploySuccessful;
            this.testsPassed = testsPassed;
        }
    }

    private static final class TestRunner {
        private Runnable onError;
        private Runnable onNone;
        private Runnable onSuccess;

        public TestRunner onError(final Runnable onError) {
            this.onError = onError;
            return this;
        }

        public TestRunner onNone(final Runnable onNone) {
            this.onNone = onNone;
            return this;
        }

        public TestRunner onSuccess(final Runnable onSuccess) {
            this.onSuccess = onSuccess;
            return this;
        }

        public Option<TestResult> run(final Project project) {
            if (project.hasTests()) {
                return Option.of(runTests(project, onSuccess, onError));
            } else {
                onNone.run();
                return Option.of(new TestResult(project, true));
            }
        }

        private TestResult runTests(final Project project, final Runnable onSuccess, final Runnable onError) {
            if ("success".equals(project.runTests())) {
                onSuccess.run();
                return new TestResult(project, true);
            } else {
                onError.run();
                return new TestResult(project, false);
            }
        }
    }

    private static final class DeployRunner {
        private Runnable onSuccess;
        private Runnable onError;

        public Option<DeployResult> deploy(final TestResult testResult) {
            if (testResult.testsPassed) {
                if ("success".equals(testResult.project.deploy())) {
                    onSuccess.run();
                    return Option.of(new DeployResult(true, testResult.testsPassed));
                } else {
                    onError.run();
                    return Option.of(new DeployResult(false, testResult.testsPassed));
                }
            }
            return Option.none();
        }

        public DeployRunner onSuccess(final Runnable onSuccess) {
            this.onSuccess = onSuccess;
            return this;
        }

        public DeployRunner onError(final Runnable onError) {
            this.onError = onError;
            return this;
        }
    }
}
