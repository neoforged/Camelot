const description = 'Sleep'
const [target] = options.optPositional(0, {description: 'Who should sleep', required: true}).parse()

function execute() {
    reply(`${target} go to sleep already`)
}
