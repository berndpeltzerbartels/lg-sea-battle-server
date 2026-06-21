import{a as e}from"./styles-DFO6iot9.js";const o="meshUboDeclaration",t=`struct Mesh {world : mat4x4<f32>,
visibility : f32,};var<uniform> mesh : Mesh;
#define WORLD_UBO
`;e.IncludesShadersStoreWGSL[o]||(e.IncludesShadersStoreWGSL[o]=t);const r="sceneUboDeclaration",n=`struct Scene {viewProjection : mat4x4<f32>,
#ifdef MULTIVIEW
viewProjectionR : mat4x4<f32>,
#endif 
view : mat4x4<f32>,
projection : mat4x4<f32>,
vEyePosition : vec4<f32>,};
#define SCENE_UBO
var<uniform> scene : Scene;
`;e.IncludesShadersStoreWGSL[r]||(e.IncludesShadersStoreWGSL[r]=n);
