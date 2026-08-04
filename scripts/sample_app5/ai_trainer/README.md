# Configure Simulation Settings

Firstly, edit config.json file in a way to declare the configuration of the simulation process used to collect training data

# Preparing Training Data

Invoke following command to convert collected data format for weka models

```
./generate_training_data.sh
```

This command creates *.arff files under the simulation results folder

# Generating Classification and Regression Models

Invoke following command to generate related weka models

```
./generate_weka_models.sh
```

This script creates weka model files under the simulation results folder. When you are done with training, you can move these files to ../config/weka/ folder
