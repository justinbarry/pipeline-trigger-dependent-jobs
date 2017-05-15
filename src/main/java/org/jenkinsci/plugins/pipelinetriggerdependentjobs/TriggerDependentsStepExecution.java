package org.jenkinsci.plugins.pipelinetriggerdependentjobs;

import groovy.lang.Script;
import hudson.model.Fingerprint;
import hudson.model.Run;
import hudson.tasks.Fingerprinter.FingerprintAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by jbarry on 5/12/17.
 *
 * This plugin identifies downstream builds via Jenkin's built in fingerprinting mechanism.
 *
 */
public class TriggerDependentsStepExecution extends StepExecution {
  private static final long serialVersionUID = 1L;
  private static final Logger LOGGER = Logger.getLogger(TriggerDependentsStep.class.getName());

  private transient TriggerDependentsStep step;
  private final transient Run<?, ?> build;

  TriggerDependentsStepExecution(StepContext context, TriggerDependentsStep step) throws Exception {
    super(context);
    this.step = step;
    this.build = context.get(Run.class);
  }

  @Override
  public boolean start() throws Exception {
    Jenkins jenkins = Jenkins.getActiveInstance();

    HashSet<String> jobsToTrigger = new HashSet<>();
    LOGGER.info("Looking for downstream jobs: " + step.getJob());

    WorkflowJob parentJob = (WorkflowJob) this.build.getParent();
    List<WorkflowJob> allJobs = jenkins.getAllItems(WorkflowJob.class);

    for(Iterator<WorkflowJob> i = allJobs.iterator(); i.hasNext();) {
      WorkflowJob j = i.next();
      if(j.getLastSuccessfulBuild() == null || j == parentJob) {
        continue;
      }

      FingerprintAction fa = j.getLastSuccessfulBuild().getAction(FingerprintAction.class);
      if(fa == null) {
        continue;
      }
      Map<String, Fingerprint> fingerprints = j.getLastSuccessfulBuild().getAction(FingerprintAction.class).getFingerprints();
      for(Fingerprint f: fingerprints.values()) {
        Fingerprint.BuildPtr original = f.getOriginal();

        if(original != null && parentJob == original.getJob()) {
          LOGGER.info("Found downstream job: " + j.getFullName());
          jobsToTrigger.add(j.getFullName());
        }
      }
    }

    for(Iterator<String> jobsToTriggerIterator = jobsToTrigger.iterator();jobsToTriggerIterator.hasNext();) {
      String actionScript = String.format(
        "build(job:\"%s\", propagate: false, quietPeriod: 30, wait: false)",
        jobsToTriggerIterator.next()
      );

      /* Execute generated script */
      CpsStepContext cps = (CpsStepContext) getContext();
      CpsThread t = CpsThread.current();
      CpsFlowExecution execution = t.getExecution();

      Script script = execution.getShell().parse(actionScript);
      cps.newBodyInvoker(t.getGroup().export(script))
        .withDisplayName("Trigger Dependency Action")
        .withCallback(BodyExecutionCallback.wrap(cps))
        .start(); // when the body is done, the flow step is done
    }

    return false;
  }

  @Override
  public void stop(@Nonnull Throwable cause) throws Exception {

  }
}
