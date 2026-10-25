import { App } from "aws-cdk-lib";
import { build } from "../lib/app";

const app = new App({ outdir: process.env.CDK_OUTDIR ?? "cdk.out" });
build(app);
app.synth();
