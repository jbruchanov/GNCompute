#version 320 es
//replaced in code
#define GROUP_SIZE -1
#define ID ((gl_WorkGroupID.x * uint(GROUP_SIZE)) + gl_LocalInvocationIndex)

layout(local_size_x = GROUP_SIZE) in;

layout(binding = 0) buffer Input {
    readonly writeonly int pixels[];
} data;
layout(location = 1) uniform int scalar;

const int MASK_A = 0xFF000000;
const int MASK_R = 0x00FF0000;
const int MASK_G = 0x0000FF00;
const int MASK_B = 0x000000FF;

void main() {
    int a = data.pixels[ID] & MASK_A;
    int r = (data.pixels[ID] & MASK_R) >> 16;
    int g = (data.pixels[ID] & MASK_G) >> 8;
    int b = (data.pixels[ID] & MASK_B);
    r = max(0, min(255, r + scalar));
    g = max(0, min(255, g + scalar));
    b = max(0, min(255, b + scalar));
    data.pixels[ID] = a | ((r << 16) | (g << 8) | b);
}
