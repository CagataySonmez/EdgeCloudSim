%--------------------------------------------------------------
%description
% returns a value according to the given arguments
%--------------------------------------------------------------
function [ret_val] = getConfiguration(argType)
    if(argType == 1)
        ret_val = '../../../sim_results/tutorial5';
    elseif(argType == 2)
        ret_val = 15; %simulation time (in minutes)
    elseif(argType == 3)
        ret_val = 10; %Number of iterations
    elseif(argType == 4)
        ret_val = 1; %x tick interval for number of mobile devices
    elseif(argType == 5)
        ret_val = {'SCENARIO1','SCENARIO2','SCENARIO3'};
    elseif(argType == 6)
        ret_val = {'Case 1','Case 2','Case 3'};
    elseif(argType == 7)
        ret_val=[6 3 15 15]; %position of figure
    elseif(argType == 8)
        ret_val = [13 12 12]; %font size for x/y label, legend and x/y axis
    elseif(argType == 9)
        ret_val = 'Number of Clients'; %Common text for x axis
    elseif(argType == 10)
        ret_val = 1000; %min number of mobile device
    elseif(argType == 11)
        ret_val = 100; %step size of mobile device count
    elseif(argType == 12)
        ret_val = 2000; %max number of mobile device
    elseif(argType == 17)
        ret_val = 0; %return 1 if you want to add 10^n text at x axis
    elseif(argType == 18)
        ret_val = 0; %return 1 if you want to save figure as pdf
    elseif(argType == 19)
        ret_val = 1; %return 1 if you want to plot errors
    elseif(argType == 20)
        ret_val= 1; %return 1 if graph is plotted colorful
    elseif(argType == 21)
        ret_val=[0.55 0 0]; %color of first line
    elseif(argType == 22)
        ret_val=[0 0.15 0.6]; %color of second line
    elseif(argType == 23)
        ret_val=[0 0.23 0]; %color of third line
    elseif(argType == 24)
        ret_val=[0.6 0 0.6]; %color of fourth line
    elseif(argType == 25)
        ret_val=[0.08 0.08 0.08]; %color of fifth line
    elseif(argType == 26)
        ret_val=[0 0.8 0.8]; %color of sixth line
    elseif(argType == 27)
        ret_val=[0.8 0.4 0]; %color of seventh line
    elseif(argType == 28)
        ret_val=[0.8 0.8 0]; %color of eighth line
    elseif(argType == 40)
        ret_val={'-k*','-ko','-ks','-kv','-kp','-kd','-kx','-kh'}; %line style (marker) of the colorless line
    elseif(argType == 50)
        ret_val={'-k*','-ko','-ks','-kv','-kp','-kd','-kx','-kh'}; %line style (marker) of the colorfull line
    end
end