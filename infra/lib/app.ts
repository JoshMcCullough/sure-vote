import { App } from "aws-cdk-lib";
import { config } from "./config";
import { EdgeStack } from "./edge-stack";
import { QueueStack } from "./queue-stack";

export function build(app: App): { edge: EdgeStack; queue: QueueStack } {
    const settings = config(app);
    const edge = new EdgeStack(app, "SureVoteEdge", { env: { region: "us-east-1" }, settings });
    const queue = new QueueStack(app, "SureVoteReceivedQueue", { env: { region: settings.queueRegion }, settings });
    return { edge, queue };
}
