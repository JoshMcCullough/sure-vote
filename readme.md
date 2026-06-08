# SureVote

SureVote is a secure and transparent voting system built on blockchain technology. It aims to provide a reliable and 
tamper-proof platform for conducting elections, ensuring the integrity of the voting process, and enhancing voter  
confidence. Each voter may independently verify that their vote was included in the tally, and any third party 
person or  group can verify the entire vote-chain on their own accord. The system utilizes cryptographic techniques 
to ensure the privacy and security of votes while maintaining transparency and cloaking vote ownership.

By leveraging blockchain's decentralized nature, SureVote eliminates the need for a central authority, reducing the 
risk of fraud and manipulation. The platform is designed to be user-friendly and accessible, allowing voters to 
participate in elections from anywhere with an internet connection, or via traditional means such as in-person 
voting and mail-in ballots.

SureVote also provides tools and services for election officials to manage and oversee the voting process, ensuring 
smooth, efficient elections. With its innovative approach to voting, SureVote aims to revolutionize the way 
elections are making them more secure, transparent, and inclusive for all participants while restoring faith in our 
voting process.

## Features

## Modules

- `entity` -- internal entities which are used for business logic and storage.
- `repository` -- storage access layer which abstracts away the underlying storage/database technology.
- `resource` -- front-facing resources which are returned from API calls.
- `api` -- API endpoints which work with the repository and entity layer and return resources.
- `service` -- business logic layer which orchestrates repository and entity interactions.

## Schema
View the [schema](./SCHEMA.md) for a detailed overview of the data structures used in SureVote.