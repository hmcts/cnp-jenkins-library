import groovy.json.JsonBuilder

class slackBlockMessage {
    private Map message

    slackBlockMessage(String initialText) {
        this.message = [
            blocks: [
                [
                    type: "section",
                    text: [
                        type: "mrkdwn",
                        text: initialText
                    ]
                ]
            ]
        ]
    }

    void setMessage(String message) {
        this.message.blocks[0].text.text = message
    }

    String getMessage(){
        return this.message.blocks[0].text.text
    }

    String toJson() {
        return new JsonBuilder(this.message).toPrettyString()
    }
}
