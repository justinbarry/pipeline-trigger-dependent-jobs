package org.jenkinsci.plugins.pipelinetriggerdependentjobs;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Set;

/**
 * Created by jbarry on 5/12/17.
 *
 */
public class TriggerDependentsStep extends Step{
  private static final String STEP_NAME = "triggerDependentJobs";

  private final int depth;

  @DataBoundConstructor
  public TriggerDependentsStep(int depth) {
    this.depth = depth;
  }

  public int getDepth() {
    return this.depth;
  }

  @Override
  public StepExecution start(StepContext context) throws Exception {
    return new TriggerDependentsStepExecution(context, this);
  }

  @Extension
  public static class DescriptorImpl extends StepDescriptor {
    @Override
    public String getFunctionName() {
      return STEP_NAME;
    }

    @Override
    public String getDisplayName() {
      return "Execute a pipeline task for the job and all its downstream jobs.";

    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return ImmutableSet.of(FilePath.class, Run.class, Launcher.class, TaskListener.class);
    }
  }
}
