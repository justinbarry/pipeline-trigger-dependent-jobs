package org.jenkinsci.plugins.pipelinetriggerdependentjobs;

import hudson.console.ModelHyperlinkNote;
import hudson.model.Cause;
import hudson.model.Fingerprint;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.tasks.Messages;

import hudson.util.RunList;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by jbarry on 5/12/17.
 *
 * This plugin identifies downstream builds via Jenkin's built in fingerprinting mechanism.
 *
 */
public class TriggerDependentsStepExecution extends SynchronousStepExecution<Void> {
  private static final long serialVersionUID = 1L;
  private static final Logger LOGGER = Logger.getLogger(TriggerDependentsStep.class.getName());

  private transient TriggerDependentsStep step;
  private final transient WorkflowRun build;

  TriggerDependentsStepExecution(StepContext context, TriggerDependentsStep step) throws Exception {
    super(context);
    this.step = step;
    this.build = context.get(WorkflowRun.class);
  }

  @Override
  protected Void run() throws Exception {
    Jenkins jenkins = Jenkins.getActiveInstance();
    WorkflowJob parentJob = this.build.getParent();

    HashSet<WorkflowJob> jobsToTrigger = new HashSet<>();
    LOGGER.info("Looking for downstream jobs: " + parentJob.getFullDisplayName());

    List<WorkflowJob> allJobs = jenkins.getAllItems(WorkflowJob.class);

    for(Iterator<WorkflowJob> i = allJobs.iterator(); i.hasNext();) {
      WorkflowJob j = i.next();
      if(j.getLastSuccessfulBuild() == null || j.getParent() == parentJob.getParent()) {
        continue;
      }


      RunList<WorkflowRun> buildList = j.getBuilds();

      Map<String, Fingerprint> fingerprints =  new TreeMap<>();
      for(WorkflowRun r: buildList) {
        FingerprintAction fa = r.getAction(FingerprintAction.class);
        if(fa == null) {
          continue;
        }

        // We are looking for the last build with reported fingerprints regardless of results.
        if(fa.getFingerprints().size() > 0) {
          fingerprints = fa.getFingerprints();
          break;
        }
      }

      for(Fingerprint f: fingerprints.values()) {
        Fingerprint.BuildPtr original = f.getOriginal();

        if(original != null && parentJob == original.getJob()) {
          LOGGER.info("Found downstream job: " + j.getFullName() + " Display name of artifact: " + f.getDisplayName());
          jobsToTrigger.add(j);
        }
      }
    }

    for(Iterator<WorkflowJob> jobsToTriggerIterator = jobsToTrigger.iterator();jobsToTriggerIterator.hasNext();) {
      WorkflowJob nextJob =  jobsToTriggerIterator.next();
      try {
        // Source from: https://github.com/jenkinsci/jenkins/blob/master/core/src/main/java/hudson/tasks/BuildTrigger.java#L297
        boolean success = nextJob.scheduleBuild(nextJob.getQuietPeriod(), new Cause.UpstreamCause(this.build));
        if (Jenkins.getInstance().getItemByFullName(nextJob.getFullName()) == nextJob) {
          String name = ModelHyperlinkNote.encodeTo(nextJob);
          if (success) {
            getContext().get(TaskListener.class).getLogger().println(Messages.BuildTrigger_Triggering(name));
          } else {
            getContext().get(TaskListener.class).getLogger().println(Messages.BuildTrigger_InQueue(name));
          }
        }
        String msg = String.format(
          "Scheduled job %s",
          nextJob.getFullName()
        );
        LOGGER.log(Level.INFO, msg);
      } catch (Exception e) {
        String msg = String.format(
          "Exception thrown when attempting to schedule build for %s",
          nextJob.getFullName()
        );
        LOGGER.log(Level.WARNING, msg, e);
      }
    }

    return null;
  }
}
