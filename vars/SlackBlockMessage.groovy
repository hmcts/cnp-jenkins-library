import groovy.json.JsonBuilder

class SlackBlockMessage {
    private List<Map> blocks
    private String color

    SlackBlockMessage() {
        this.blocks = []
        // Default to green
        this.color = '#6aa84f'
    }

    void addSection(String text) {
        this.blocks.add([
            type: "section",
            text: [
                type: "mrkdwn",
                text: text
            ]
        ])
    }

    void addHeader(String text) {
        this.blocks.add([
            type: "header",
            text: [
                type: "plain_text",
                text: text,
                emoji: true
            ]
        ])
    }

    void addDivider() {
        this.blocks.add([
            type: "divider"
        ])
    }

    void addField(String text) {
        def lastBlock = this.blocks.last()
        if (lastBlock.type == "section" && lastBlock.containsKey("fields")) {
            lastBlock.fields.add([
                type: "mrkdwn",
                text: text
            ])
        } else {
            this.blocks.add([
                type: "section",
                fields: [[
                    type: "mrkdwn",
                    text: text
                ]]
            ])
        }
    }

    void setColor(String color){
        this.color = color
    }

    Map<String, Object>  asObject() {
        return [
            color: this.color,
            blocks: this.blocks
        ]
    }
}
