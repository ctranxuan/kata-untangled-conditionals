import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        private final Project project;

        /*
         * Passing the project could be an asset: once a project steps are done,
         * we could trigger steps of another project
         */
        Step(final Function<Project, StepState> execute, Project project) {
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

    private final Config config;
    private final Emailer emailer;
    private final Logger log;

    public Pipeline(Config config, Emailer emailer, Logger log) {
        this.config = config;
        this.emailer = emailer;
        this.log = log;
    }

    public void run(Project project) {
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

        // Chain of responsibility (see https://github.com/mariofusco/from-gof-to-lambda)
        Stream.of(runTests, deployProject)
              .filter(step -> !step.nextStep())
              .findFirst(); // get the first step that fails

        if (!config.sendEmailSummary()) {
            log.info("Email disabled");
        }
    }

    private StepState deploy(final Project project) {
        return StepState.of("success".equals(project.deploy()));
    }

    private void sendEmail(final Config config, final String message) {
        if (config.sendEmailSummary()) {
            log.info("Sending email");
            emailer.send(message);
        }
    }

    private StepState runTests(final Project project) {
        return project.hasTests()
               ? StepState.of("success".equals(project.runTests()))
               : StepState.SKIPPED;
    }
}
