/*
 * Title:        EdgeCloudSim - Edge Orchestrator
 * 
 * Description: 
 * EdgeOrchestrator is an abstract class which is used for selecting VM
 * for each client requests. For those who wants to add a custom 
 * Edge Orchestrator to EdgeCloudSim should extend this class and provide
 * a concreate instance via ScenarioFactory
 *               
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2017, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.edge_orchestrator;

import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.Task;

public abstract class EdgeOrchestrator {
	protected String policy;
	protected SimSettings.SCENARIO_TYPES simScenario;
	
	public EdgeOrchestrator(String _policy, SimSettings.SCENARIO_TYPES _simScenario){
		policy = _policy;
		simScenario = _simScenario;
	}
	
	/*
	 * initialize edge orchestrator if needed
	 */
	public abstract void initialize();
	
	/*
	 * returns proper VM from the related edge orchestrator point of view
	 */
	public abstract EdgeVM selectVm(Task task);
}
