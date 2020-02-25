#!/usr/bin/env node

const document = JSON.parse(process.argv[2]);

const config = {
  endpoint: process.env.COSMOS_DB_URL,
  key: process.env.COSMOSDB_TOKEN_KEY,
  databaseId: "jenkins",
  containerId: process.env.COSMOS_COLLECTION_ID,
};

const CosmosClient = require('@azure/cosmos').CosmosClient;


async function run() {
const client = new CosmosClient({ endpoint: config.endpoint, key: config.key });

const { database } = await client.database(config.databaseId).read();
const { container } = await database.container(config.containerId).read();
const itemResponse = await container.items.create(document);

console.log(`Created ${itemResponse.item.id}`)

}

run()
  .then()
  .catch(err => console.log(err));
