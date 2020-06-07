import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import dependencies.Config;
import dependencies.Emailer;
import dependencies.Logger;
import dependencies.Project;

public class Pipeline {
    private enum StepState {
        SUCCESS, FAILED, SKIPPED;

        static StepState of(final boolean success) {
            return success ? SUCCESS : FAILED;
        }
    }

    private static final class Step {
        private final List<Runnable> onSuccess = new ArrayList<>();
        private final List<Runnable> onFail = new ArrayList<>();
        private final List<Runnable> onSkip = new ArrayList<>();
        private final Function<Project, StepState> execute;

        Step(final Function<Project, StepState> execute) {
            this.execute = execute;
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

        /**
         * Executes the step with the given project.
         *
         * @param project the project on which the step is executed.
         * @return {@code true} if and only if the next step can be executed.
         */
        boolean execute(final Project project) {
            final StepState state = execute.apply(project);
            switch (state) {
            case SUCCESS:
                onSuccess.forEach(Runnable::run);
                return true;
            case FAILED:
                onFail.forEach(Runnable::run);
                return false;
            case SKIPPED:
                onSkip.forEach(Runnable::run);
                return true;
            default:
                throw new IllegalArgumentException("unknown state:" + state);
            }
        }
    }

    private static final class MailSender {
        private boolean firstCall = true;
        private final Config config;
        private final Emailer emailer;
        private final Logger log;

        private MailSender(final Config config, final Emailer emailer, final Logger log) {
            this.config = config;
            this.emailer = emailer;
            this.log = log;
        }

        private void sendEmail(final String message) {
            if (firstCall) {
                firstCall = false;
                logMailProcess(config);
            }
            if (config.sendEmailSummary()) {
                emailer.send(message);
            }
        }

        private void logMailProcess(final Config config) {
            if (config.sendEmailSummary()) {
                log.info("Sending email");
            } else {
                log.info("Email disabled");
            }
        }
    }

    private final Logger log;
    private final MailSender mailSender;

    public Pipeline(Config config, Emailer emailer, Logger log) {
        this.mailSender = new MailSender(config, emailer, log);
        this.log = log;
    }

    public void run(Project project) {
        final Step runTests = new Step(this::runTests)
                                    .onSuccess(() -> log.info("Tests passed"))
                                    .onFail(() -> log.error("Tests failed"))
                                    .onFail(() -> sendEmail("Tests failed"))
                                    .onSkip(() -> log.info("No tests"));

        final Step deployProject = new Step(this::deploy)
                                    .onSuccess(() -> log.info("Deployment successful"))
                                    .onSuccess(() -> sendEmail("Deployment completed successfully"))
                                    .onFail(() -> log.error("Deployment failed"))
                                    .onFail(() -> sendEmail("Deployment failed"));

        // Chain of responsibility (see https://github.com/mariofusco/from-gof-to-lambda)
        Stream.of(runTests, deployProject)
              .filter(step -> !step.execute(project))
              .findFirst(); // get the first step that fails
    }

    private StepState runTests(final Project project) {
        return project.hasTests()
               ? StepState.of("success".equals(project.runTests()))
               : StepState.SKIPPED;
    }

    private StepState deploy(final Project project) {
        return StepState.of("success".equals(project.deploy()));
    }

    private void sendEmail(final String message) {
        mailSender.sendEmail(message);
    }
}
