%--------------------------------------------------------------
%description
% returns a value according to the given argumentssss
%--------------------------------------------------------------
function [ret_val] = getConfiguration(argType)
    if(argType == 1)
        ret_val = 'D:\sim_results';
    elseif(argType == 2)
        ret_val = 1; %Number of iterations
    elseif(argType == 3)
        ret_val = 100; %min number of mobile device
    elseif(argType == 4)
        ret_val = 100; %step size of mobile device count
    elseif(argType == 5)
        ret_val =1000; %max number of mobile device
    elseif(argType == 6)
        ret_val = 1; %xTickLabelCoefficient
    elseif(argType == 7)
        ret_val = {'SINGLE_TIER','TWO_TIER','TWO_TIER_WITH_EO'};
    elseif(argType == 8)
        ret_val = {'1-tier','2-tier','2-tier with EO'};
    elseif(argType == 9)
        ret_val=[10 3 12 12]; %position of figure
    elseif(argType == 10)
        ret_val = 'Number of Mobile Devices'; %Common text for x axis
    elseif(argType == 11)
        ret_val = 1; %return 1 if you want to save figure as pdf
    elseif(argType == 12)
        ret_val = 0; %return 1 if you want to plot errors
    elseif(argType == 20)
        ret_val=1; %return 1 if graph is plotted colerful
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
        ret_val={'-k*','-ko','-ks','-kv','-kp','-kd','-kx','-kh'}; %line style (marker) of the colerless line
    elseif(argType == 50)
        ret_val={':k*',':ko',':ks',':kv',':kp',':kd',':kx',':kh'}; %line style (marker) of the colerfull line
    end
end