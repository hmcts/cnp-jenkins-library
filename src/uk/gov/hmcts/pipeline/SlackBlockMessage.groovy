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
    
     // Adds a section block with markdown text if text is not empty
    void addSection(String text) {
        if(!text.isEmpty()){
            this.blocks.add([
                type: "section",
                text: [
                    type: "mrkdwn",
                    text: text
                ]
            ])
        }
    }

    // Adds a header block with plain text - adds at position where it is called
    void addHeader(String text) {
        this.blocks.add([
            type: "header",
            text: [
                type: "plain_text",
                text: text,
            ]
        ])
    }

    // Adds a header block with plain text, guaranteed at first position of message
    void addFirstHeader(String text) {
        this.blocks.add(0, [
            type: "header",
            text: [
                type: "plain_text",
                text: text,
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

    // Returns the message as a list of maps to meet attachment criteria of slack send function, including the color and blocks
    List<Map<String, Object>> asObject() {
        return [
            [
                color: this.color,
                blocks: this.blocks
            ]
        ]
    }
}
