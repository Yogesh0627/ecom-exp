/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Emit a self-contained server bundle (.next/standalone) so the Docker image is tiny and needs
  // no node_modules at runtime.
  output: 'standalone',
  // Product/fridge images come from S3/MinIO/CDN in production. Allow common remote hosts;
  // tighten to the real bucket domain before launch.
  images: {
    remotePatterns: [
      { protocol: 'https', hostname: '**' },
    ],
  },
};

export default nextConfig;
