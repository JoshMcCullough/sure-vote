import { Duration, RemovalPolicy, Stack, StackProps, CfnOutput } from "aws-cdk-lib";
import * as iam from "aws-cdk-lib/aws-iam";
import * as kms from "aws-cdk-lib/aws-kms";
import * as sqs from "aws-cdk-lib/aws-sqs";
import { Construct } from "constructs";
import { SureVoteConfig } from "./config";

export interface QueueStackProps extends StackProps {
    readonly settings: SureVoteConfig;
}

export class QueueStack extends Stack {
    readonly queue: sqs.Queue;
    readonly access: iam.ManagedPolicy;

    constructor(scope: Construct, id: string, props: QueueStackProps) {
        super(scope, id, props);
        const key = new kms.Key(this, "ReceivedKey", {
            description: "Encrypts issuance ids waiting for their received mark",
            enableKeyRotation: true,
            removalPolicy: RemovalPolicy.RETAIN,
        });
        this.queue = new sqs.Queue(this, "Received", {
            encryption: sqs.QueueEncryption.KMS,
            encryptionMasterKey: key,
            dataKeyReuse: Duration.minutes(5),
            retentionPeriod: Duration.days(4),
            visibilityTimeout: Duration.minutes(props.settings.queueVisibilityMinutes),
            receiveMessageWaitTime: Duration.seconds(0),
            enforceSSL: true,
            removalPolicy: RemovalPolicy.DESTROY,
        });
        this.access = new iam.ManagedPolicy(this, "ReceivedAccess", {
            description: "Lets the SureVote API send, receive, put back, and delete received-queue ids",
            statements: [
                new iam.PolicyStatement({
                    actions: [
                        "sqs:SendMessage",
                        "sqs:ReceiveMessage",
                        "sqs:DeleteMessage",
                        "sqs:ChangeMessageVisibility",
                        "sqs:GetQueueAttributes",
                    ],
                    resources: [this.queue.queueArn],
                }),
                new iam.PolicyStatement({
                    actions: ["kms:GenerateDataKey", "kms:Decrypt"],
                    resources: [key.keyArn],
                }),
            ],
        });
        new CfnOutput(this, "ReceivedQueueUrl", { value: this.queue.queueUrl });
        new CfnOutput(this, "ReceivedAccessPolicy", { value: this.access.managedPolicyArn });
    }
}
