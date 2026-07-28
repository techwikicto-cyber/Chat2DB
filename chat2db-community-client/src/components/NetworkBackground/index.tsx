import { memo, useEffect, useRef } from 'react';
import { useTheme } from 'antd-style';
import { useStyles } from './style';

const PARTICLE_COUNT = 40;
const CONNECTION_DISTANCE = 120;
const PARTICLE_RADIUS = 2;
const DOT_ALPHA = 0.55;
const LINE_ALPHA = 0.35;

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
}

/**
 * Drifting points joined by lines where they come close - the same figure the
 * rest of the product line uses behind its sign-in screen.
 *
 * Sized to the element rather than the window, drawn at the display's own pixel
 * density so the lines stay crisp, and coloured from the theme so it reads in
 * light and dark alike. Still when the viewer has asked for reduced motion: the
 * points are laid out once and left there, since a field of slowly moving dots
 * is exactly what that setting is about.
 */
const NetworkBackground = memo(() => {
  const { styles } = useStyles();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const token = useTheme();
  const dotColor = token.colorTextTertiary;
  const lineColor = token.colorTextQuaternary;

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext('2d');
    if (!canvas || !context) {
      return;
    }

    let particles: Particle[] = [];
    let width = 0;
    let height = 0;
    let frame = 0;

    const still = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

    const resize = () => {
      const rect = canvas.getBoundingClientRect();
      if (!rect.width || !rect.height) {
        return;
      }
      const ratio = window.devicePixelRatio || 1;
      width = rect.width;
      height = rect.height;
      canvas.width = Math.round(width * ratio);
      canvas.height = Math.round(height * ratio);
      context.setTransform(ratio, 0, 0, ratio, 0, 0);
      if (!particles.length) {
        particles = Array.from({ length: PARTICLE_COUNT }, () => ({
          x: Math.random() * width,
          y: Math.random() * height,
          vx: (Math.random() - 0.5) * 0.5,
          vy: (Math.random() - 0.5) * 0.5,
        }));
      }
    };

    const draw = () => {
      context.clearRect(0, 0, width, height);

      particles.forEach((particle) => {
        if (!still) {
          particle.x += particle.vx;
          particle.y += particle.vy;
          if (particle.x < 0 || particle.x > width) {
            particle.vx = -particle.vx;
          }
          if (particle.y < 0 || particle.y > height) {
            particle.vy = -particle.vy;
          }
        }

        context.globalAlpha = DOT_ALPHA;
        context.fillStyle = dotColor;
        context.beginPath();
        context.arc(particle.x, particle.y, PARTICLE_RADIUS, 0, Math.PI * 2);
        context.fill();
      });

      context.strokeStyle = lineColor;
      for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
          const dx = particles[i].x - particles[j].x;
          const dy = particles[i].y - particles[j].y;
          const distance = Math.sqrt(dx * dx + dy * dy);
          if (distance >= CONNECTION_DISTANCE) {
            continue;
          }
          // Both the width and the opacity fall away with distance, so a pair
          // drifting apart fades out instead of snapping off.
          const closeness = 1 - distance / CONNECTION_DISTANCE;
          context.globalAlpha = LINE_ALPHA * closeness;
          context.lineWidth = closeness;
          context.beginPath();
          context.moveTo(particles[i].x, particles[i].y);
          context.lineTo(particles[j].x, particles[j].y);
          context.stroke();
        }
      }
      context.globalAlpha = 1;
    };

    const animate = () => {
      draw();
      frame = window.requestAnimationFrame(animate);
    };

    resize();
    if (still) {
      draw();
    } else {
      animate();
    }

    // Follows the element, which the window's own resize event does not: the
    // card sits in a panel that can change size on its own.
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(resize);
    observer?.observe(canvas);

    return () => {
      observer?.disconnect();
      window.cancelAnimationFrame(frame);
    };
  }, [dotColor, lineColor]);

  return <canvas ref={canvasRef} className={styles.canvas} aria-hidden />;
});

export default NetworkBackground;
