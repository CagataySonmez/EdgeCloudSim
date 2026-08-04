function [] = plotTimeComplexity()

    plotGenericLine(6, 1, 'Simulation Time (minute)', 'ALL_APPS', 'NorthWest', 0, 60);
    
    plotGenericLine(6, 2, 'Orchestration Algorithm Overhead (micro second)', 'ALL_APPS', 'NorthEast', 0, 1000);
    
end