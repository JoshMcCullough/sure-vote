import { App } from "aws-cdk-lib";
import { config } from "./config";
import { DemoStack } from "./demo-stack";
import { EdgeStack } from "./edge-stack";
import { IngestStack } from "./ingest-stack";
import { QueueStack } from "./queue-stack";
import { WorkspaceAccessStack } from "./workspace-access-stack";

export function build(app: App): {
    edge: EdgeStack;
    demo: DemoStack;
    workspace: WorkspaceAccessStack;
    ingest: IngestStack;
    queue: QueueStack;
} {
    const settings = config(app);
    const edge = new EdgeStack(app, "SureVoteEdge", { env: { region: "us-east-1" }, settings });
    const demo = new DemoStack(app, "SureVoteDemoEdge", { env: { region: "us-east-1" }, settings });
    const workspace = new WorkspaceAccessStack(app, "SureVoteWorkspaceAccess", {
        env: { region: settings.workspaceRegion },
        settings,
    });
    const ingest = new IngestStack(app, "SureVoteIngestEdge", { env: { region: "us-east-1" }, settings });
    const queue = new QueueStack(app, "SureVoteReceivedQueue", { env: { region: settings.queueRegion }, settings });
    return { edge, demo, workspace, ingest, queue };
}
