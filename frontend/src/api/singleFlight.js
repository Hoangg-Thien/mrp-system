export function singleFlight(run) {
  let pending
  return (...args) => {
    if (!pending) pending = Promise.resolve().then(() => run(...args)).finally(() => { pending = undefined })
    return pending
  }
}
