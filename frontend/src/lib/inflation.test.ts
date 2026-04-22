import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as inflationLib from './inflation'

vi.mock('./api', () => ({
  default: {
    post: vi.fn(),
  },
}))

import api from './api'

describe('uploadInflation', () => {
  beforeEach(() => vi.clearAllMocks())

  it('retries once after a 429 response and succeeds', async () => {
    const tooManyRequests = { response: { status: 429 } }
    const successResponse = { data: { purchasesCreated: 1, purchasesSkipped: 0, itemsImported: 3 } }

    vi.mocked(api.post)
      .mockRejectedValueOnce(tooManyRequests)
      .mockResolvedValueOnce(successResponse)

    const file = new File(['x'], 'jan.xls')
    const result = await inflationLib.uploadInflation(file)

    expect(api.post).toHaveBeenCalledTimes(2)
    expect(result.purchasesCreated).toBe(1)
  })

  it('throws after exhausting all retries on 429', async () => {
    const tooManyRequests = { response: { status: 429 } }
    vi.mocked(api.post).mockRejectedValue(tooManyRequests)

    const file = new File(['x'], 'jan.xls')
    await expect(inflationLib.uploadInflation(file)).rejects.toMatchObject({
      response: { status: 429 },
    })
    expect(api.post).toHaveBeenCalledTimes(3) // 1 tentativa + 2 retries
  })

  it('does not retry on non-429 errors', async () => {
    const serverError = { response: { status: 500 } }
    vi.mocked(api.post).mockRejectedValue(serverError)

    const file = new File(['x'], 'jan.xls')
    await expect(inflationLib.uploadInflation(file)).rejects.toMatchObject({
      response: { status: 500 },
    })
    expect(api.post).toHaveBeenCalledTimes(1)
  })
})
