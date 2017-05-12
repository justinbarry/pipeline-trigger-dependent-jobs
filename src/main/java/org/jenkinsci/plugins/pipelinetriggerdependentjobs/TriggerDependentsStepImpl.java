package org.jenkinsci.plugins.pipelinetriggerdependentjobs;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.Nonnull;

/**
 * Created by jbarry on 5/12/17.
 */
public class TriggerDependentsStepImpl extends StepExecution {
  @Override
  public boolean start() throws Exception {
    return true;
  }

  @Override
  public void stop(@Nonnull Throwable cause) throws Exception {

  }
}
