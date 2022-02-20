#version 320 es
//replaced in code, can't be just gl_WorkGroupSize.x as it's used in declaration
#define GROUP_SIZE -1
#define ID ((gl_WorkGroupID.x * uint(GROUP_SIZE)) + gl_LocalInvocationIndex)

layout(local_size_x = GROUP_SIZE) in;

layout(binding = 0) buffer Input {
    readonly float data[GROUP_SIZE];
} inputData;

layout(binding = 1) buffer Output {
    writeonly float data[GROUP_SIZE];
} outputData;

float simpleOp(float[GROUP_SIZE] data, uint index) {
    return data[index] + 0.5;
}

void main() {
    outputData.data[ID] = simpleOp(inputData.data, ID);
}
