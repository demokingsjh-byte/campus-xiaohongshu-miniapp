export const custom = Symbol.for('nodejs.util.inspect.custom')

export const inspect = Object.assign(
  (value: unknown) => {
    try {
      return typeof value === 'string' ? value : JSON.stringify(value)
    } catch {
      return String(value)
    }
  },
  { custom }
)

export default {
  custom,
  inspect
}
