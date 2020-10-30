import pandas as pd
import json
import sys

if len (sys.argv) != 5:
    print('invalid arguments. Usage:')
    print('python data_conventor.py config.json [edge|cloud_rsu|cloud_gsm] [classifier|regression] [train|test]')
    sys.exit(1)
    
with open(sys.argv[1]) as json_data_file:
    data = json.load(json_data_file)
    
target = sys.argv[2]
method = sys.argv[3]
datatype = sys.argv[4]

print("conversion started with args " + target + ", " + method + ", " + datatype)

sim_result_folder = data["sim_result_folder"]
num_iterations = data["num_iterations"]
train_data_ratio = data["train_data_ratio"]
min_vehicle = data["min_vehicle"]
max_vehicle = data["max_vehicle"]
vehicle_step_size = data["vehicle_step_size"]

def getDecisionColumnName(target):
    if target == "edge":
        COLUMN_NAME  = "EDGE"
    elif target == "cloud_rsu":
        COLUMN_NAME  = "CLOUD_VIA_RSU"
    elif target == "cloud_gsm":
        COLUMN_NAME  = "CLOUD_VIA_GSM"
    return COLUMN_NAME

def getClassifierColumns(target):
    if target == "edge":
        result  = ["NumOffloadedTask", "TaskLength", "WLANUploadDelay", "WLANDownloadDelay", "AvgEdgeUtilization", "Result"]
    elif target == "cloud_rsu":
        result  = ["NumOffloadedTask", "WANUploadDelay", "WANDownloadDelay", "Result"]
    elif target == "cloud_gsm":
        result  = ["NumOffloadedTask", "GSMUploadDelay", "GSMDownloadDelay", "Result"]
    return result

def getRegressionColumns(target):
    if target == "edge":
        result = ["TaskLength", "AvgEdgeUtilization", "ServiceTime"]
    elif target == "cloud_rsu":
        result = ["TaskLength", "WANUploadDelay", "WANDownloadDelay", "ServiceTime"]
    elif target == "cloud_gsm":
        result = ["TaskLength", "GSMUploadDelay", "GSMDownloadDelay", "ServiceTime"]
    return result

def znorm(column):
    column = (column - column.mean()) / column.std()
    return column

data_set =  []

testDataStartIndex = (train_data_ratio * num_iterations) / 100

for ite in range(num_iterations):
    for vehicle in range(min_vehicle, max_vehicle+1, vehicle_step_size):
        if (datatype == "train" and ite < testDataStartIndex) or (datatype == "test" and ite >= testDataStartIndex):
            file_name = sim_result_folder + "/ite" + str(ite + 1) + "/" + str(vehicle) + "_learnerOutputFile.cvs"
            df = [pd.read_csv(file_name, na_values = "?", comment='\t', sep=",")]
            df[0]['VehicleCount'] = vehicle
            #print(file_name)
            data_set += df

data_set = pd.concat(data_set, ignore_index=True)
data_set = data_set[data_set['Decision'] == getDecisionColumnName(target)]

if method == "classifier":
    targetColumns = getClassifierColumns(target)
else:
    targetColumns= getRegressionColumns(target)

if datatype == "train":
    print ("##############################################################")
    print ("Stats for " + target + " - " + method)
    print ("Please use relevant information from below table in java side:")
    train_stats = data_set[targetColumns].describe()
    train_stats = train_stats.transpose()
    print(train_stats)
    print ("##############################################################")

#print("balancing " + target + " for " + method)

#BALANCE DATA SET
if method == "classifier":
    df0 = data_set[data_set['Result']=="fail"]
    df1 = data_set[data_set['Result']=="success"]
    
    #size = min(len(df0[df0['VehicleCount']==max_vehicle]), len(df1[df1['VehicleCount']==min_vehicle]))
    
    size = len(df0[df0['VehicleCount']==max_vehicle]) // 2
    
    df1 = df1.groupby('VehicleCount').apply(lambda x: x if len(x) < size else x.sample(size))
    df0 = df0.groupby('VehicleCount').apply(lambda x: x if len(x) < size else x.sample(size))

    data_set = pd.concat([df0, df1], ignore_index=True)
else:        
    data_set = data_set[data_set['Result'] == 'success']
    
    #size = min(len(data_set[data_set['VehicleCount']==min_vehicle]), len(data_set[data_set['VehicleCount']==max_vehicle]))
    
    size = len(data_set[data_set['VehicleCount']==max_vehicle]) // 3
    data_set = data_set.groupby('VehicleCount').apply(lambda x: x if len(x.index) < size else x.sample(size))

#EXTRACT RELATED ATTRIBUTES
df = pd.DataFrame(columns=targetColumns)
for column in targetColumns:
    if column == 'Result' or column == 'ServiceTime':
        df[column] = data_set[column]
    else:
        df[column] = znorm(data_set[column])

f = open(sim_result_folder + "/" + target + "_" + method + "_" + datatype + ".arff", 'w')
f.write('@relation ' + target + '\n\n')
for column in targetColumns:
    if column == 'Result':
        f.write('@attribute class {fail,success}\n')
    else:
        f.write('@attribute ' + column + ' REAL\n')
f.write('\n@data\n')
df.to_csv(f, header=False, index=False)
f.close()

print ("##############################################################")
print ("Operation completed!")
print (".arff file is generated for weka.")
print ("##############################################################")

