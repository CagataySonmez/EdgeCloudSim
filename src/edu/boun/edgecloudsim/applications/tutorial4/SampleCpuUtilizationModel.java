/* SPDX-License-Identifier: GPL-3.0-or-later */
/*
 * Title:        EdgeCloudSim - Custom VM Cpu Utilization Model
 *
 * Description:
 * Demonstrates a VM CPU-utilization estimate derived from task length and VM
 * capacity. Alternative implementations can be supplied through ScenarioFactory.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial4;

import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.Vm;

import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.edge_client.Task;

/** Estimates a task's CPU demand relative to the assigned VM capacity. */
public class SampleCpuUtilizationModel implements UtilizationModel {
	private Task task;

	/** Creates an unbound utilization model; call {@link #setTask(Task)} before use. */
	public SampleCpuUtilizationModel(){
	}

	// VM CPU utilization model:
	// - Predicts utilization percentage per task as (taskLength / vmMips) * 100.
	// - getUtilization(time) ignores 'time' (static estimate used by placement).
	// - Scenario-specific simplification (no time-varying profile).
	@Override
	public double getUtilization(double time) {
		// Fetch bound VM via task association then compute deterministic estimate
		Vm vm = SimManager.getInstance().getMobileDeviceManager().getVmList().get(task.getAssociatedVmId());

		return predictUtilization(vm);
	}

	/**
	 * Binds the task whose utilization is to be evaluated.
	 *
	 * @param _task task to bind
	 */
	public void setTask(Task _task){
		task=_task;
	}

	/**
	 * Estimates the bound task's utilization on a VM.
	 *
	 * @param vm VM whose capacity supplies the denominator
	 * @return task length divided by VM MIPS, expressed as a percentage
	 */
	public double predictUtilization(Vm vm){
		// Linear mapping: proportion of VM capacity consumed by total instruction length
		// NOTE: Assumes single task perspective or average share; no overlap modeling
		// The tutorial defines utilization as 100 * task length / VM capacity.
		return (double)100 * (task.getCloudletLength() / vm.getMips());
	}
}
