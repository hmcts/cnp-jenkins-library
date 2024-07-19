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

    void addHeader(String text) {
        def length = text.length()
        println("Character count of header is: ${length}")
        this.blocks.add([
            type: "header",
            text: [
                type: "plain_text",
                text: text,
            ]
        ])
        addDivider()
    }


    void addFirstHeader(String text) {
        def length = text.length()
        println("Character count of header is: ${length}")
        this.blocks.add(0, [
            type: "header",
            text: [
                type: "plain_text",
                text: text,
            ]
        ])
        this.blocks.add(1, [
            type: "divider"
        ])
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
}
