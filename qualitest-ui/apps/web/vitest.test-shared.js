/** Vitest 多 project 共用配置（node / jsdom） */
export const vitestTestShared = {
  setupFiles: ['./src/test/vitest-setup.ts'],
  reporters: ['verbose'],
}

export const vitestTestInclude = [
  'src/test/**/*.test.ts',
  'src/test/**/*.spec.ts',
]

export const vitestJsdomInclude = [
  'src/test/utils/**/*.test.ts',
  'src/test/utils/**/*.spec.ts',
]
