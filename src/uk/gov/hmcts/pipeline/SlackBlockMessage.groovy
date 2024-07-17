package uk.gov.hmcts.pipeline
import groovy.json.JsonBuilder

class SlackBlockMessage {
    private List<Map> blocks
    private String color

    SlackBlockMessage() {
        this.blocks = []
        // Default to green color
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
        addDivider()
    }

    void addDivider() {
        this.blocks.add([
            type: "divider"
        ])
    }

    void setDangerColor(){
        // Sets color to red
        this.color = '#ef333f'
    }

    void setWarningColor(){
        // Sets color to orange
        this.color = '#ff781f'
    }

    List<Map<String, Object>> asObject() {
        return [
            [
                color: this.color,
                blocks: this.blocks
            ]
        ]
    }

    List<Map> getBlocks() {
        return this.blocks
    }
}
