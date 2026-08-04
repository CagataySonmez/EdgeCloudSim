function [] = plotTimeComplexity()

    plotGenericLine(6, 1, 'Simulation Time (minute)', 'ALL_APPS', '', 'NorthWest', 60);
    plotGenericLine(6, 2, 'Orchestration Algorithm Overhead (micro second)', 'ALL_APPS', '', 'NorthEast', 1000);
    
end