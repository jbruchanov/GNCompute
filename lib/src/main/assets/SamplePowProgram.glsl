#version 320 es
//replaced in code
#define GROUP_SIZE -1
#define ID ((gl_WorkGroupID.x * uint(GROUP_SIZE)) + gl_LocalInvocationIndex)

layout(local_size_x = GROUP_SIZE) in;

layout(binding = 0) buffer Input {
    readonly writeonly float items[];
} data;
layout(location = 1) uniform float exponent;


void main() {
    data.items[ID] = pow(data.items[ID], exponent);
}
