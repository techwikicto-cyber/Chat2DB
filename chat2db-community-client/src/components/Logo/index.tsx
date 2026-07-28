import { memo } from 'react';
import { Flex } from 'antd';
import { type LogoProps } from '@chat2db/ui';
import { useProductName } from '@/hooks/useProductName';
import owlMark from '@/assets/logo/community/bina-owl.png';

/**
 * Product mark.
 *
 * Rendered here rather than through the design system's `Logo`, which pairs a
 * bundled image with the upstream wordmark - both of them the wrong brand for
 * this build, and the wordmark is not something `image` can replace.
 *
 * Keeps the upstream prop shape so call sites are unchanged.
 */
export default memo<LogoProps>(({ type = 'image', size = 24, image, className, appName: _appName, ...rest }) => {
  const src = image || owlMark;
  const name = useProductName();

  if (type === 'text') {
    return (
      <span className={className} style={{ fontSize: size / 2, fontWeight: 700, whiteSpace: 'nowrap' }}>
        {name}
      </span>
    );
  }

  const mark = <img src={src} height={size} width={size} alt={name} />;

  if (type === 'imageWithText') {
    return (
      <Flex className={className} justify="flex-start" align="center" gap={size / 4} {...rest}>
        {mark}
        <span style={{ fontSize: size / 1.8, fontWeight: 700, whiteSpace: 'nowrap' }}>{name}</span>
      </Flex>
    );
  }

  return (
    <img src={src} className={className} height={size} width={size} alt={name} />
  );
});
